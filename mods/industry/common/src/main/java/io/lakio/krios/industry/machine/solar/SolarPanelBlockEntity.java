package io.lakio.krios.industry.machine.solar;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.network.EnergyPort;
import io.lakio.krios.industry.network.EnergySink;
import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.Flow;
import io.lakio.krios.industry.network.FlowPorts;
import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkManager;
import io.lakio.krios.industry.network.NetworkNode;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Passive daylight producer (design 2026-10-01, terminal revision same
 * day). LIT = producing (day + sees sky + room to store or push); only a
 * LIT panel ticks. Unlit, it keeps a slow scheduled-wake chain (ADR-017)
 * asking "can I produce?" — rather than computing dawn: scheduled ticks
 * count REAL ticks, so {@code /time set}, bed skips and
 * {@code doDaylightCycle} would strand a computed wake, while the poll
 * self-heals them all within ~5 s for ~120 checks a night. Rain halves
 * the rate but never zeroes it (no weather transitions to track — Mk I
 * only, higher tiers will be rain-immune). A machine of full standing:
 * generator-style terminal, energy-out face config (manual contracts
 * gate third-party extraction), auto-config negotiation on contact.
 * Mk II will add boost slots (generator-style bay).
 */
public class SolarPanelBlockEntity extends BlockEntity
        implements MenuProvider, NetworkNode, EnergyPort,
        io.lakio.krios.industry.machine.ConfigurableFaces,
        io.lakio.krios.industry.machine.EnergyStats {

    /** Banks its nights (user call): produces less than a generator, stores more. */
    public static final int CAPACITY = 100_000;
    /** Park-ratio frame: one panel = two induction furnaces while the sun shines. */
    public static final int RATE_CLEAR = 8;
    public static final int RATE_RAIN = 4;
    public static final int MAX_EXTRACT_PER_TICK = 400;
    /** Unlit poll cadence (ADR-017): dawn, cleared skies, freed buffer — all within ~5 s. */
    public static final int POLL_INTERVAL_TICKS = 100;

    // Production states, synced for the terminal's work area and voice.
    public static final int STATE_NIGHT = 0;
    public static final int STATE_CLEAR = 1;
    public static final int STATE_RAIN = 2;
    public static final int STATE_FULL = 3;
    public static final int STATE_COVERED = 4;

    // Energy out is the panel's whole vocabulary (no inputs of any kind).
    private static final short PRESET_OUT_BITS = (short) FaceConfig.flowBits(Flow.ENERGY, true);
    /** Single source for the GUI hologram columns and the in-world overlay. */
    public static final short SUPPORTED_FACE_BITS = PRESET_OUT_BITS;

    private final EnergyBuffer energy = new EnergyBuffer(CAPACITY, MAX_EXTRACT_PER_TICK);
    private final FaceConfig faceConfig = new FaceConfig();
    /** Live output rate, for diagnostics; 0 while dark or saturated. */
    private int currentRate;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> currentRate;
                case 1 -> productionState();
                case 2 -> energy.getStored() & 0xFFFF;
                case 3 -> (energy.getStored() >> 16) & 0xFFFF;
                case 10 -> networkKoiosLevel();
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
            return SolarPanelMenu.DATA_COUNT;
        }
    };

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    /** Same button channel as the other machines (face*16 + sub). */
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
                faceConfig.enterManual(face, PRESET_OUT_BITS);
            }
        } else if (sub >= 9 && sub <= 12) {
            faceConfig.enterManual(face, (short) switch (sub) {
                case 9, 10 -> 0;  // nothing flows in: "input only" = blocked
                default -> PRESET_OUT_BITS;
            });
        } else if (sub < 8 && faceConfig.isManual(face)) {
            faceConfig.toggleManual(face, Flow.VALUES[sub / 2], sub % 2 == 1);
        } else {
            return false;
        }
        setChanged();
        syncFacesToClients();
        return true;
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

    // --- Flow ports: energy source on every face (auto-config pairs with
    // consumers on contact, like the generator) ---

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
     * Face contract for external energy extraction (FE/TRE adapters and
     * the offline contact push). Only a MANUAL config restricts — virgin
     * and auto-configured faces keep vanilla behavior (exposed).
     */
    public boolean allowsEnergyExtract(Direction side) {
        return !faceConfig.isManual(side) || faceConfig.allows(side, Flow.ENERGY, true);
    }

    // --- Production ---

    /** 0 at night or buried; halved under rain AT THIS POSITION (deserts stay dry). */
    private int productionRate(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        if (!level.isDay() || !level.canSeeSky(above)) {
            return 0;
        }
        return level.isRainingAt(above) ? RATE_RAIN : RATE_CLEAR;
    }

    /** Synced terminal state: why the panel does (not) produce right now. */
    private int productionState() {
        if (level == null) {
            return STATE_NIGHT;
        }
        BlockPos above = worldPosition.above();
        if (!level.canSeeSky(above)) {
            return STATE_COVERED;
        }
        if (!level.isDay()) {
            return STATE_NIGHT;
        }
        if (energy.isFull()) {
            return STATE_FULL;
        }
        return level.isRainingAt(above) ? STATE_RAIN : STATE_CLEAR;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarPanelBlockEntity be) {
        // Only reachable while LIT (the block registers no ticker otherwise).
        int rate = be.productionRate(level, pos);
        if (rate <= 0) {
            be.goDark(level, pos, state);
            return;
        }
        be.currentRate = rate;
        be.energy.insert(rate);
        be.distributeEnergy(level, pos, NetworkManager.networkOf(be));
        if (be.energy.isFull()) {
            // Saturated and nobody drains: idle dark, the poll resumes us.
            be.goDark(level, pos, state);
            return;
        }
        be.setChanged();
    }

    private void goDark(Level level, BlockPos pos, BlockState state) {
        currentRate = 0;
        level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
        level.scheduleTick(pos, state.getBlock(), POLL_INTERVAL_TICKS);
        setChanged();
    }

    /** Slow poll while dark (ADR-017): relight, or keep the chain alive. */
    public void pollWake() {
        if (level == null || level.isClientSide) {
            return;
        }
        tryResume();
        if (!getBlockState().getValue(BlockStateProperties.LIT)) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), POLL_INTERVAL_TICKS);
        }
    }

    /** Relight when the sun shines and the buffer has room again. */
    public void tryResume() {
        if (level != null && !level.isClientSide
                && !getBlockState().getValue(BlockStateProperties.LIT)
                && !energy.isFull() && productionRate(level, worldPosition) > 0) {
            level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, true), 3);
        }
    }

    /**
     * Proactive distribution, mirror of the generator: pooled fair-share
     * push on a valid network, contact push into touching consumers
     * offline — only the producer is ever awake (ADR-012). Both face
     * contracts gate the offline push.
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

    // --- Energy port (network pool + platform extract-only adapters) ---

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

    public EnergyBuffer getEnergy() {
        return energy;
    }

    // --- Energy diagnostics (Calibrator, KOIOS alerts) ---

    @Override
    public int energyDrawPerTick() {
        return 0;
    }

    @Override
    public int energySupplyPerTick() {
        return getBlockState().getValue(BlockStateProperties.LIT) ? currentRate : 0;
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

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.solar_panel");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SolarPanelMenu(containerId, playerInventory, this, dataAccess);
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
        energy.save(tag);
        faceConfig.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.load(tag);
        faceConfig.load(tag);
    }
}
