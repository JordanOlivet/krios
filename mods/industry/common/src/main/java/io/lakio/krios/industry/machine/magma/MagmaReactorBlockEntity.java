package io.lakio.krios.industry.machine.magma;

import io.lakio.krios.core.energy.EnergyBuffer;
import io.lakio.krios.core.koios.KoiosCoreBlockEntity;
import io.lakio.krios.core.network.EnergyPort;
import io.lakio.krios.core.network.EnergySink;
import io.lakio.krios.core.network.FaceConfig;
import io.lakio.krios.core.network.Flow;
import io.lakio.krios.core.network.FlowPorts;
import io.lakio.krios.core.network.Network;
import io.lakio.krios.core.network.NetworkManager;
import io.lakio.krios.core.network.NetworkNode;
import io.lakio.krios.industry.IndustryBalance;
import io.lakio.krios.industry.machine.ItemFlow;
import io.lakio.krios.industry.machine.LitDevice;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.TieredMachine;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Magma Reactor (design 2026-11-27, ADR-039): the lava specialist born at
 * tier 2. Twice the Thermal Reactor's energy from the same lava, taken in
 * bulk into a tank — from buckets, from a lava cauldron on a manual fluid-in
 * face, or from other mods' pipes through the loaders' fluid capabilities.
 * Same burner as the Thermal Reactor: LIT device (ADR-027), buffer
 * thermostat, pool distribution online, contact push offline.
 */
public class MagmaReactorBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, NetworkNode, EnergyPort,
        io.lakio.krios.core.network.ConfigurableFaces, io.lakio.krios.industry.machine.ContactEnergySource,
        io.lakio.krios.core.energy.EnergyStats,
        io.lakio.krios.core.tick.TickingDevice, io.lakio.krios.core.tick.DeviceAlertable {

    /** Energy per millibucket: twice the Thermal Reactor's (320,000 E a bucket there). */
    public static final int ENERGY_PER_MB = 640;
    /** Base output, times the effective tier's factor: 64 E/t at tier 2. */
    public static final int GENERATION_PER_TICK = 32;
    /** Burn ticks per millibucket at the base rate (640 / 32). */
    public static final int BURN_TICKS_PER_MB = ENERGY_PER_MB / GENERATION_PER_TICK;
    /** Lava drawn from the tank per burn cycle; the gauge moves in small steps. */
    public static final int BURN_CYCLE_MB = 100;
    /** Buffer per hardware tier: 200,000 E at tier 2, the Thermal's reserve in time. */
    public static final int CAPACITY_PER_TIER = 100_000;
    /** Tank per hardware tier: 16,000 mB (16 buckets) at tier 2. */
    public static final int TANK_PER_TIER = 8_000;
    public static final int MAX_EXTRACT_PER_TICK = 800;

    public static final int BUCKET_IN_SLOT = 0;
    public static final int BUCKET_OUT_SLOT = 1;
    /** Tier 2 machines open four module slots; the bay takes Energy modules only. */
    public static final int MODULE_SLOT_FIRST = 2;
    public static final int MODULE_SLOT_COUNT = 4;
    public static final int MODULE_SLOT_LAST = MODULE_SLOT_FIRST + MODULE_SLOT_COUNT - 1;
    public static final int INVENTORY_SIZE = MODULE_SLOT_LAST + 1;
    /** Empty buckets wait in a stack of 16 for a hopper or an item-out face. */
    public static final int BUCKET_OUT_STACK = 16;

    /** Seat on the ADR-027 device scheduler. */
    private final LitDevice device = new LitDevice();

    // Primitive runtime state (ADR-012). No object allocation on the tick path.
    private final int machineTier;
    private final EnergyBuffer energy;
    private final LavaTank tank;
    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final FaceConfig faceConfig = new FaceConfig();
    private int burnTime;
    private int burnDuration;
    /** Lava burned since the last KOIOS report: one operation per bucket. */
    private int burnedMb;
    /** Live output rate (base x effective-tier factor x counted Energy points), for stats. */
    private int currentGenerationPerTick = GENERATION_PER_TICK;

    // ContainerData values are truncated to 16-bit shorts on sync: energy and
    // lava ship as low/high pairs. Capacities follow from the tier.
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> burnDuration;
                case 2 -> energy.getStored() & 0xFFFF;
                case 3 -> (energy.getStored() >> 16) & 0xFFFF;
                case 10 -> networkKoiosLevel();
                case 11 -> worldPosition.getX() & 0xFFFF;
                case 12 -> (worldPosition.getX() >> 16) & 0xFFFF;
                case 13 -> worldPosition.getZ() & 0xFFFF;
                case 14 -> (worldPosition.getZ() >> 16) & 0xFFFF;
                case 15 -> worldPosition.getY();
                case 16 -> machineTier;
                case 17 -> MachineTiers.effectiveTier(
                        NetworkManager.networkOf(MagmaReactorBlockEntity.this), machineTier);
                case 18 -> tank.getStored() & 0xFFFF;
                case 19 -> (tank.getStored() >> 16) & 0xFFFF;
                // 4..9: raw face-config shorts (11 bits, short-safe).
                default -> index >= 4 && index < 10
                        ? faceConfig.bits(Direction.from3DDataValue(index - 4)) : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; menus never write back.
        }

        @Override
        public int getCount() {
            return MagmaReactorMenu.DATA_COUNT;
        }
    };

    // Face presets: in = buckets and lava in; out = energy and empty buckets
    // out. Lava out is never a preset's side effect: a pipe in extract mode
    // must not empty a generator unless the player set FLUID out by hand.
    private static final short PRESET_IN_BITS =
            (short) (FaceConfig.flowBits(Flow.ITEM, false) | FaceConfig.flowBits(Flow.FLUID, false));
    private static final short PRESET_OUT_BITS =
            (short) (FaceConfig.flowBits(Flow.ITEM, true) | FaceConfig.flowBits(Flow.ENERGY, true));
    private static final short FLUID_OUT_BITS = (short) FaceConfig.flowBits(Flow.FLUID, true);
    /** Single source for the GUI hologram columns and the in-world overlay. */
    public static final short SUPPORTED_FACE_BITS = (short) (PRESET_IN_BITS | PRESET_OUT_BITS | FLUID_OUT_BITS);

    public MagmaReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAGMA_REACTOR.get(), pos, state);
        machineTier = state.getBlock() instanceof TieredMachine tiered ? tiered.machineTier() : 2;
        energy = new EnergyBuffer(CAPACITY_PER_TIER * machineTier, MAX_EXTRACT_PER_TICK);
        tank = new LavaTank(TANK_PER_TIER * machineTier);
    }

    public int machineTier() {
        return machineTier;
    }

    public LavaTank getLavaTank() {
        return tank;
    }

    public EnergyBuffer getEnergy() {
        return energy;
    }

    /** Same button channel as the other machines (face*16 + sub, 9..12 = presets). */
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
                default -> SUPPORTED_FACE_BITS;
            });
        } else if (sub < 8 && faceConfig.isManual(face)) {
            faceConfig.toggleManual(face, Flow.VALUES[sub / 2], sub % 2 == 1);
        } else {
            return false;
        }
        setChanged();
        syncFacesToClients();
        // A manual item or fluid face may just have appeared: enter the wake chain.
        ItemFlow.alertMachine(level, worldPosition);
        return true;
    }

    // --- Network node ---

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide) {
            NetworkManager.addNode(this);
            device.register(level, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        device.remove();
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
            tryIgnite();
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

    private void syncFacesToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // --- Face contracts. A manual face is its complete contract; a virgin
    // face keeps vanilla behavior for energy, items and lava IN. ---

    /** External energy extraction (FE/TRE adapters, the offline contact push). */
    public boolean allowsEnergyExtract(Direction side) {
        return !faceConfig.isManual(side) || faceConfig.allows(side, Flow.ENERGY, true);
    }

    /** Lava from other mods' pipes: virgin faces and manual FLUID-in faces. */
    public boolean allowsFluidInsert(Direction side) {
        return !faceConfig.isManual(side) || faceConfig.allows(side, Flow.FLUID, false);
    }

    /** Lava out only through a face the player set to FLUID out by hand. */
    public boolean allowsFluidExtract(Direction side) {
        return faceConfig.isManual(side) && faceConfig.allows(side, Flow.FLUID, true);
    }

    private boolean allowsItemIn(Direction side) {
        return !faceConfig.isManual(side) || faceConfig.allows(side, Flow.ITEM, false);
    }

    private boolean allowsItemOut(Direction side) {
        return !faceConfig.isManual(side) || faceConfig.allows(side, Flow.ITEM, true);
    }

    /** A manual FLUID-in face: the only kind that empties a touching lava cauldron. */
    private boolean hasManualFluidInFace() {
        for (Direction dir : Direction.values()) {
            if (faceConfig.isManual(dir) && faceConfig.allows(dir, Flow.FLUID, false)) {
                return true;
            }
        }
        return false;
    }

    /** Scheduled probing: manual item faces (bucket pulls and pushes) or a manual fluid-in face. */
    private boolean needsServicing() {
        return ItemFlow.hasManualItemFace(faceConfig) || hasManualFluidInFace();
    }

    // --- Lava in ---

    /**
     * The tank changed from outside (a pipe through the fluid capability):
     * persist, relight, and let a freed tank take the next bucket.
     */
    public void onLavaChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            pourBucket();
            tryIgnite();
            device.alert();
        }
    }

    /**
     * A full lava bucket in the input slot pours into the tank when a whole
     * bucket fits and the output slot can take the empty one.
     */
    private void pourBucket() {
        ItemStack in = items.get(BUCKET_IN_SLOT);
        if (!in.is(Items.LAVA_BUCKET) || tank.room() < LavaTank.BUCKET) {
            return;
        }
        ItemStack out = items.get(BUCKET_OUT_SLOT);
        if (!out.isEmpty() && (!out.is(Items.BUCKET) || out.getCount() >= BUCKET_OUT_STACK)) {
            return;
        }
        tank.fill(LavaTank.BUCKET, false);
        in.shrink(1);
        if (out.isEmpty()) {
            items.set(BUCKET_OUT_SLOT, new ItemStack(Items.BUCKET));
        } else {
            out.grow(1);
        }
        setChanged();
        tryIgnite();
    }

    /**
     * Manual FLUID-in faces empty touching lava cauldrons (the vanilla
     * dripstone farm's renewable lava). Virgin faces never do: a decorative
     * cauldron beside the machine stays full.
     */
    private void drainCauldrons() {
        for (Direction dir : Direction.values()) {
            if (tank.room() < LavaTank.BUCKET) {
                return;
            }
            if (!faceConfig.isManual(dir) || !faceConfig.allows(dir, Flow.FLUID, false)) {
                continue;
            }
            BlockPos cauldron = worldPosition.relative(dir);
            if (level.isLoaded(cauldron) && level.getBlockState(cauldron).is(Blocks.LAVA_CAULDRON)) {
                level.setBlockAndUpdate(cauldron, Blocks.CAULDRON.defaultBlockState());
                level.playSound(null, cauldron, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 0.5F, 1.0F);
                tank.fill(LavaTank.BUCKET, false);
                setChanged();
                tryIgnite();
            }
        }
    }

    // --- Burner ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, MagmaReactorBlockEntity be) {
        // Only reachable while LIT (deviceTick gates it, ADR-027). Tier
        // doubling: rate AND burn speed together, so lava's energy is
        // constant; counted Energy points raise the rate only (ADR-039).
        Network network = NetworkManager.networkOf(be);
        boolean online = network != null && network.isValid();
        int effectiveTier = MachineTiers.effectiveTier(network, be.machineTier);
        int factor = MachineTiers.speedFactor(effectiveTier);
        int energyPoints = online
                ? MachineTiers.countedEnergyPoints(be.modulePoints(ModuleType.ENERGY), effectiveTier) : 0;
        be.currentGenerationPerTick = GENERATION_PER_TICK * factor * (4 + energyPoints) / 4;
        // Buffer thermostat (2026-10-02): a burn only ticks when the whole
        // tick's output fits; a full buffer pauses mid-cycle, the first
        // drain resumes it.
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
        if (be.burnTime <= 0 && !be.tryStartBurn()) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
        }
        be.setChanged();
    }

    private boolean hasRoomForTick() {
        return energy.getCapacity() - energy.getStored() >= currentGenerationPerTick;
    }

    /** Relight hysteresis, as the Thermal Reactor: ~100 ticks' worth of room. */
    private static final int RESUME_ROOM_TICKS = 100;

    private boolean hasResumeRoom() {
        return energy.getCapacity() - energy.getStored()
                >= currentGenerationPerTick * RESUME_ROOM_TICKS;
    }

    /**
     * Starts the next burn cycle from the tank: up to {@link #BURN_CYCLE_MB},
     * so the last drops burn too. Every full bucket burned is one KOIOS
     * operation under the Thermal Reactor's lava key, so lava stays one fuel
     * for diversity whatever its path.
     */
    private boolean tryStartBurn() {
        if (!hasRoomForTick()) {
            return false;
        }
        int mb = tank.drain(BURN_CYCLE_MB, false);
        if (mb <= 0) {
            return false;
        }
        burnTime = mb * BURN_TICKS_PER_MB;
        burnDuration = burnTime;
        burnedMb += mb;
        if (burnedMb >= LavaTank.BUCKET) {
            int buckets = burnedMb / LavaTank.BUCKET;
            burnedMb -= buckets * LavaTank.BUCKET;
            Network network = NetworkManager.networkOf(this);
            if (network != null && network.controller() instanceof KoiosCoreBlockEntity core) {
                core.reportOperations(LAVA_XP_KEY, buckets, IndustryBalance.BURN_BASE_XP);
            }
        }
        // The tank has room again: the next bucket may pour.
        pourBucket();
        return true;
    }

    /** The Thermal Reactor's key for a lava bucket ("burn:" + the fuel item's id). */
    public static final String LAVA_XP_KEY = "burn:minecraft:lava_bucket";

    private void tryIgnite() {
        if (level == null || level.isClientSide
                || getBlockState().getValue(BlockStateProperties.LIT)) {
            return;
        }
        // A paused burn resumes before any lava is drawn: it already paid for that energy.
        if (hasResumeRoom() && (burnTime > 0 || tryStartBurn())) {
            level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, true), 3);
            device.alert();
        }
    }

    /** Pool online, touching consumers offline; both face contracts respected. */
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

    /** A neighbor changed (placed/removed/mutated): resume, ignite, probe. */
    public void onNeighborChanged() {
        if (level != null && !level.isClientSide) {
            tryIgnite();
            ItemFlow.alertMachine(level, worldPosition);
        }
    }

    /**
     * Scheduled wake (ADR-017 cadence): pull lava buckets through manual
     * item-in faces, empty cauldrons on manual fluid-in faces, pour, and push
     * the empty buckets out through manual item-out faces.
     */
    private void scheduledWake() {
        ItemFlow.pullIntoSlot(level, worldPosition, this, BUCKET_IN_SLOT, faceConfig, 1);
        drainCauldrons();
        pourBucket();
        ItemFlow.pushOutput(level, worldPosition, this, BUCKET_OUT_SLOT, faceConfig);
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

    // --- Modules ---

    /** Point total on one axis (points model: Mk I = 1, Mk II = 2). */
    private int modulePoints(ModuleType type) {
        return ModuleItem.pointsIn(this, MODULE_SLOT_FIRST, MODULE_SLOT_COUNT, type, -1);
    }

    /** Energy points counted at the live effective tier (ADR-039); 0 off the network. */
    public int countedEnergyPoints() {
        Network network = NetworkManager.networkOf(this);
        if (network == null || !network.isValid()) {
            return 0;
        }
        return MachineTiers.countedEnergyPoints(modulePoints(ModuleType.ENERGY),
                MachineTiers.effectiveTier(network, machineTier));
    }

    /** Two slots per hardware tier, as every tier 2 machine: four at tier 2. */
    public int activeModuleSlots() {
        return Math.min(MODULE_SLOT_COUNT, 2 * machineTier);
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.magma_reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MagmaReactorMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- Container ---

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
        if (slot == BUCKET_OUT_SLOT && level != null && !level.isClientSide) {
            // Room for the next empty bucket: a waiting lava bucket may pour.
            pourBucket();
        }
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
        if (level != null && !level.isClientSide && slot <= BUCKET_OUT_SLOT) {
            pourBucket();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Hopper discipline: lava buckets into the input, nothing else
        // anywhere (the menu's module slots carry the player-facing gate).
        return slot == BUCKET_IN_SLOT && stack.is(Items.LAVA_BUCKET);
    }

    private static final int[] IN_ONLY = {BUCKET_IN_SLOT};
    private static final int[] OUT_ONLY = {BUCKET_OUT_SLOT};
    private static final int[] IN_AND_OUT = {BUCKET_IN_SLOT, BUCKET_OUT_SLOT};
    private static final int[] NO_SLOTS = {};

    @Override
    public int[] getSlotsForFace(Direction side) {
        boolean in = allowsItemIn(side);
        boolean out = allowsItemOut(side);
        return in && out ? IN_AND_OUT : in ? IN_ONLY : out ? OUT_ONLY : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack) && allowsItemIn(side);
    }

    /** Only the empty buckets come out: never lava, never modules. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == BUCKET_OUT_SLOT && allowsItemOut(side);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // --- Client sync: the face contract rides the vanilla BE update tag. ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        faceConfig.save(tag);
        if (!tag.contains("Faces")) {
            tag.putIntArray("Faces", new int[6]);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Device scheduler (ADR-027): awake while LIT ---

    @Override
    public BlockPos devicePos() {
        return worldPosition;
    }

    @Override
    public void alertDevice() {
        device.alert();
    }

    @Override
    public void deviceTick(ServerLevel level, long now) {
        if (device.pullDue(now, needsServicing())) {
            scheduledWake();
        }
        BlockState state = getBlockState();
        if (state.getValue(BlockStateProperties.LIT)) {
            serverTick(level, worldPosition, state, this);
        } else {
            tryIgnite();
        }
        device.settle(getBlockState().getValue(BlockStateProperties.LIT), needsServicing(), now);
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        energy.save(tag);
        tank.save(tag);
        faceConfig.save(tag);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnDuration", burnDuration);
        tag.putInt("LavaBurned", burnedMb);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy.load(tag);
        tank.load(tag);
        faceConfig.load(tag);
        burnTime = tag.getInt("BurnTime");
        burnDuration = tag.getInt("BurnDuration");
        burnedMb = tag.getInt("LavaBurned");
    }
}
