package io.lakio.krios.industry.machine.reactor;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.TieredMachine;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.network.EnergyPort;
import io.lakio.krios.industry.network.EnergySink;
import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.Flow;
import io.lakio.krios.industry.network.FlowPorts;
import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkManager;
import io.lakio.krios.industry.network.NetworkNode;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.Direction;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ThermalReactorBlockEntity extends BlockEntity
        implements Container, MenuProvider, NetworkNode, EnergyPort,
        io.lakio.krios.industry.machine.ConfigurableFaces,
        io.lakio.krios.industry.machine.EnergyStats {

    /** ~2 coals of reserve; a full reactor idles unlit (ADR-012). */
    public static final int CAPACITY = 50_000;
    public static final int FUEL_SLOT = 0;
    /**
     * Energy-module bay (design 2026-10-01): the t1 reactor stays
     * deliberately boring (slots inert), tier 2 activates both. ENERGY
     * modules only — +25%/pt energy from the same fuel.
     */
    public static final int MODULE_SLOT_FIRST = 1;
    public static final int MODULE_SLOT_COUNT = 2;
    public static final int MODULE_SLOT_LAST = MODULE_SLOT_FIRST + MODULE_SLOT_COUNT - 1;
    public static final int INVENTORY_SIZE = MODULE_SLOT_LAST + 1;
    /**
     * Energy scale (design 2026-09-30, revised after play — the strict
     * vanilla-parity anchor starved everything): one reactor feeds a
     * small machine park, 4 plasma forges at full speed (4 E/t
     * each) or ~3 grinders. Burn time stays vanilla, so one coal
     * (1600 ticks) = 25,600 E = 64 induction smelts — 8x its stone
     * furnace value; that multiplier is the tech reward.
     */
    public static final int GENERATION_PER_TICK = 16;
    public static final int MAX_EXTRACT_PER_TICK = 400;

    // Primitive runtime state (ADR-012). No object allocation on the tick path.
    private final int machineTier;
    private final EnergyBuffer energy;
    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final FaceConfig faceConfig = new FaceConfig();
    private int burnTime;
    private int burnDuration;
    /** Live output rate (base x effective-tier factor), for stats/diag. */
    private int currentGenerationPerTick = GENERATION_PER_TICK;

    // ContainerData values are truncated to 16-bit shorts on sync, so the
    // energy value ships as a low/high pair. Capacity is a constant and is
    // never synced.
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> burnDuration;
                case 2 -> energy.getStored() & 0xFFFF;
                case 3 -> (energy.getStored() >> 16) & 0xFFFF;
                case 10 -> networkKoiosLevel();
                // 16..17: tier plumbing (hardware tier, live effective tier).
                case 16 -> machineTier;
                case 17 -> MachineTiers.effectiveTier(
                        NetworkManager.networkOf(ThermalReactorBlockEntity.this), machineTier);
                // 11..15: machine pos for the hologram view (x/z as low/high
                // short pairs, y fits a short on its own).
                case 11 -> worldPosition.getX() & 0xFFFF;
                case 12 -> (worldPosition.getX() >> 16) & 0xFFFF;
                case 13 -> worldPosition.getZ() & 0xFFFF;
                case 14 -> (worldPosition.getZ() >> 16) & 0xFFFF;
                case 15 -> worldPosition.getY();
                // 4..9: raw face-config shorts (11 bits, short-safe).
                default -> index < 10
                        ? faceConfig.bits(Direction.from3DDataValue(index - 4)) : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; menus never write back.
        }

        @Override
        public int getCount() {
            return ThermalReactorMenu.DATA_COUNT;
        }
    };

    // Directions this machine supports, for the one-click presets.
    private static final short PRESET_IN_BITS = (short) FaceConfig.flowBits(Flow.ITEM, false);
    private static final short PRESET_OUT_BITS = (short) FaceConfig.flowBits(Flow.ENERGY, true);
    /** Single source for the GUI hologram columns and the in-world overlay. */
    public static final short SUPPORTED_FACE_BITS = (short) (PRESET_IN_BITS | PRESET_OUT_BITS);

    /** Same button channel as the grinder (face*16 + sub, 9..12 = presets). */
    public boolean handleFaceButton(int id) {
        int faceIndex = id / 16;
        int sub = id % 16;
        if (faceIndex < 0 || faceIndex > 5) {
            return false;
        }
        Direction face = Direction.from3DDataValue(faceIndex);
        if (sub == 15) {
            if (faceConfig.isManual(face)) {
                faceConfig.resetToVirgin(face);
            } else {
                faceConfig.enterManual(face, (short) (PRESET_IN_BITS | PRESET_OUT_BITS));
            }
        } else if (sub >= 9 && sub <= 12) {
            faceConfig.enterManual(face, (short) switch (sub) {
                case 9 -> 0;
                case 10 -> PRESET_IN_BITS;
                case 11 -> PRESET_OUT_BITS;
                default -> PRESET_IN_BITS | PRESET_OUT_BITS;
            });
        } else if (sub < 8 && faceConfig.isManual(face)) {
            faceConfig.toggleManual(face, Flow.VALUES[sub / 2], sub % 2 == 1);
        } else {
            return false;
        }
        setChanged();
        syncFacesToClients();
        // A manual item face may just have appeared: enter the wake chain.
        io.lakio.krios.industry.machine.ItemFlow.scheduleWake(level, worldPosition, faceConfig);
        return true;
    }

    public ThermalReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THERMAL_REACTOR.get(), pos, state);
        machineTier = state.getBlock() instanceof TieredMachine tiered
                ? tiered.machineTier() : 1;
        // Hardware buffers scale with the machine tier (~2 coals of reserve).
        energy = new EnergyBuffer(CAPACITY * machineTier, MAX_EXTRACT_PER_TICK);
    }

    public int machineTier() {
        return machineTier;
    }

    // --- Network node ---

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide) {
            NetworkManager.addNode(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            NetworkManager.removeNode(this);
        }
    }

    @Override
    public BlockPos networkPos() {
        return worldPosition;
    }

    @Override
    public Level networkLevel() {
        return level;
    }

    /** KOIOS level of this machine's network controller; 0 while offline. */
    public int networkKoiosLevel() {
        Network network = NetworkManager.networkOf(this);
        return network != null && network.controller() instanceof KoiosCoreBlockEntity core
                ? core.getKoiosLevel() : 0;
    }

    /** Pool contribution: rate-capped by the buffer, wakes the burner. */
    @Override
    public int extractNetworkEnergy(int max) {
        int pulled = energy.extract(max);
        if (pulled > 0) {
            setChanged();
            tryResume();
        }
        return pulled;
    }

    @Override
    public int storedNetworkEnergy() {
        return energy.getStored();
    }

    @Override
    public int networkEnergyCapacity() {
        return energy.getCapacity();
    }

    // --- Flow ports: energy source on every face ---

    @Override
    public boolean canOffer(Direction face, Flow flow) {
        return flow == Flow.ENERGY;
    }

    @Override
    public boolean canAccept(Direction face, Flow flow) {
        return false;
    }

    @Override
    public FaceConfig faceConfig() {
        return faceConfig;
    }

    @Override
    public short supportedFaceBits() {
        return SUPPORTED_FACE_BITS;
    }

    @Override
    public void faceConfigDirty() {
        // Never setChanged() here: auto-config can run during chunk load
        // and the comparator scan would sync-load chunks (deadlock).
        if (level != null) {
            NetworkManager.markUnsaved(level, worldPosition);
            syncFacesToClients();
        }
    }

    /**
     * Face contract to watching clients (in-world Calibrator overlay).
     * Safe where setChanged is not: blockChanged only queues on an already
     * present chunk holder, no comparator scan, no chunk loading.
     */
    private void syncFacesToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Face contract for external energy extraction (FE/TRE adapters and the
     * disconnected contact transfer). Only a MANUAL config restricts —
     * virgin and auto-configured faces keep vanilla behavior (exposed).
     */
    public boolean allowsEnergyExtract(Direction side) {
        return !faceConfig.isManual(side) || faceConfig.allows(side, Flow.ENERGY, true);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ThermalReactorBlockEntity be) {
        // Only reachable while LIT (the block registers no ticker otherwise).
        // Tier doubling: rate AND burn speed scale together, so a fuel's
        // total energy never changes — higher tiers just deliver it faster.
        // Energy modules are the one exception by design: +25%/pt on the
        // rate with the burn untouched = more energy from the same fuel.
        Network network = NetworkManager.networkOf(be);
        boolean online = network != null && network.isValid();
        int factor = MachineTiers.speedFactor(
                MachineTiers.effectiveTier(network, be.machineTier));
        int energyPoints = online ? be.modulePoints(ModuleType.ENERGY) : 0;
        be.currentGenerationPerTick = GENERATION_PER_TICK * factor * (4 + energyPoints) / 4;
        // Buffer thermostat (user call 2026-10-02): the goal state is every
        // buffer full. A burn only ticks when the whole tick's output fits,
        // so fuel pays exactly for banked energy — when the buffer tops up
        // mid-cycle the reactor PAUSES with its remaining burn preserved
        // (extinguished, tickless) and the first drain relights it
        // (extractNetworkEnergy -> tryResume picks the cycle back up).
        if (!be.hasRoomForTick()) {
            be.distributeEnergy(level, pos, network);
            if (!be.hasRoomForTick()) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
                be.setChanged();
                return;
            }
        }
        be.energy.insert(be.currentGenerationPerTick);
        be.distributeEnergy(level, pos, network);
        be.burnTime -= factor;
        if (be.burnTime <= 0 && !be.tryConsumeFuel()) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
        }
        be.setChanged();
    }

    /** One full tick of output fits: the burn can tick without waste. */
    private boolean hasRoomForTick() {
        return energy.getCapacity() - energy.getStored() >= currentGenerationPerTick;
    }

    /**
     * Relight hysteresis: resuming at the first missing unit would strobe
     * LIT every couple of ticks at equilibrium (steady drain on a full
     * buffer). A paused reactor waits for ~100 ticks' worth of room
     * (~3% of the buffer — invisible on the gauge) so burn cycles run in
     * seconds-long bursts instead.
     */
    private static final int RESUME_ROOM_TICKS = 100;

    private boolean hasResumeRoom() {
        return energy.getCapacity() - energy.getStored()
                >= currentGenerationPerTick * RESUME_ROOM_TICKS;
    }

    /**
     * Proactive distribution (design: buffers fill as soon as energy
     * exists): while burning, the surplus streams into the network's sinks,
     * or into touching consumers offline — idle machines charge without
     * having to tick (ADR-012 stays intact: only the producer is awake).
     */
    private void distributeEnergy(Level level, BlockPos pos, Network network) {
        if (network != null && network.isValid()) {
            int offer = Math.min(MAX_EXTRACT_PER_TICK, energy.getStored());
            if (offer > 0) {
                int accepted = network.offerEnergy(offer, this);
                if (accepted > 0) {
                    energy.consume(accepted);
                }
            }
            return;
        }
        // Offline contact push: both face contracts respected.
        for (Direction direction : Direction.values()) {
            int offer = Math.min(MAX_EXTRACT_PER_TICK, energy.getStored());
            if (offer <= 0) {
                return;
            }
            if (!allowsEnergyExtract(direction)) {
                continue;
            }
            if (level.getBlockEntity(pos.relative(direction)) instanceof EnergySink sink
                    && sink instanceof FlowPorts ports
                    && (!ports.faceConfig().isManual(direction.getOpposite())
                            || ports.faceConfig().allows(direction.getOpposite(), Flow.ENERGY, false))) {
                int accepted = sink.insertNetworkEnergy(offer);
                if (accepted > 0) {
                    energy.consume(accepted);
                }
            }
        }
    }

    /** A neighbor changed (placed/removed/mutated): resume or ignite. */
    public void onNeighborChanged() {
        if (level != null && !level.isClientSide) {
            tryResume();
            tryIgnite();
            io.lakio.krios.industry.machine.ItemFlow.scheduleWake(level, worldPosition, faceConfig);
        }
    }

    /**
     * Scheduled wake (ADR-017): poll the manual item-in faces for fuel —
     * setItem relights the burner — then keep the chain alive.
     */
    public void scheduledWake() {
        if (level == null || level.isClientSide) {
            return;
        }
        io.lakio.krios.industry.machine.ItemFlow.pullIntoSlot(level, worldPosition, this, 0,
                faceConfig, KoiosBalance.ITEM_PULL_BATCH_BASE * MachineTiers.speedFactor(
                        MachineTiers.effectiveTier(NetworkManager.networkOf(this), machineTier)));
        io.lakio.krios.industry.machine.ItemFlow.scheduleWake(level, worldPosition, faceConfig);
    }

    // --- Energy diagnostics (Calibrator, KOIOS alerts) ---

    @Override
    public int energyDrawPerTick() {
        return 0;
    }

    @Override
    public int energySupplyPerTick() {
        return getBlockState().getValue(BlockStateProperties.LIT) ? currentGenerationPerTick : 0;
    }

    @Override
    public int energyConsumedPerTick() {
        return 0;
    }

    @Override
    public boolean energyStarved() {
        return false;
    }

    @Override
    public int energyStored() {
        return energy.getStored();
    }

    @Override
    public int energyCapacity() {
        return energy.getCapacity();
    }

    /** Starts the next burn cycle if fuel is available and the buffer has room. */
    private boolean tryConsumeFuel() {
        if (!hasRoomForTick()) {
            return false;
        }
        ItemStack fuel = items.get(0);
        int time = Services.PLATFORM.getBurnTime(fuel);
        if (time <= 0) {
            return false;
        }
        // Burn cycles feed the KOIOS diversity window keyed by fuel item, so
        // varied fuels teach more than a coal monoculture. Reported to this
        // machine's network controller — no controller, no learning.
        Network network = NetworkManager.networkOf(this);
        if (network != null && network.controller() instanceof KoiosCoreBlockEntity core) {
            core.reportOperations(
                    "burn:" + BuiltInRegistries.ITEM.getKey(fuel.getItem()), 1, KoiosBalance.BURN_BASE_XP);
        }
        fuel.shrink(1);
        burnTime = time;
        burnDuration = time;
        return true;
    }

    /** Called when fuel lands in the slot or a drain frees room while off. */
    private void tryIgnite() {
        if (level == null || level.isClientSide
                || getBlockState().getValue(BlockStateProperties.LIT)) {
            return;
        }
        // A paused burn resumes before any new item is consumed: the item
        // already paid for that energy (thermostat, 2026-10-02).
        if (hasResumeRoom() && (burnTime > 0 || tryConsumeFuel())) {
            level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, true), 3);
        }
    }

    /**
     * A full reactor idles unlit and resumes when a drain frees room —
     * consumers poke it after pulling (v0 adjacency; the network's scheduled
     * wake-ups take over later).
     */
    public void tryResume() {
        tryIgnite();
    }

    public EnergyBuffer getEnergy() {
        return energy;
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.thermal_reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ThermalReactorMenu(containerId, playerInventory, this, dataAccess);
    }

    /** Point total on one axis (points model: Mk I = 1, Mk II = 2). */
    private int modulePoints(ModuleType type) {
        int points = 0;
        for (int slot = MODULE_SLOT_FIRST; slot <= MODULE_SLOT_LAST; slot++) {
            if (items.get(slot).getItem() instanceof ModuleItem module && module.getType() == type) {
                points += module.points();
            }
        }
        return points;
    }

    /** The bay only exists at tier 2+ (t1 stays deliberately boring). */
    public int activeModuleSlots() {
        return machineTier >= 2 ? MODULE_SLOT_COUNT : 0;
    }

    // --- Container (fuel + module bay; hoppers only ever see the fuel) ---

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
        tryIgnite();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Hopper discipline: fuel only, never into the module bay (the
        // menu's ModuleSlot carries the player-facing module gate).
        return slot == FUEL_SLOT && Services.PLATFORM.getBurnTime(stack) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // --- Client sync: the face contract rides the vanilla BE update tag
    // (chunk load + sendBlockUpdated) for the in-world Calibrator overlay;
    // everything else stays menu-synced. ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        faceConfig.save(tag);
        if (!tag.contains("Faces")) {
            // All-virgin still ships: an empty tag would be dropped and a
            // client that saw manual bits would keep them after a reset.
            tag.putIntArray("Faces", new int[6]);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        energy.save(tag);
        faceConfig.save(tag);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnDuration", burnDuration);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy.load(tag);
        faceConfig.load(tag);
        burnTime = tag.getInt("BurnTime");
        burnDuration = tag.getInt("BurnDuration");
    }
}
