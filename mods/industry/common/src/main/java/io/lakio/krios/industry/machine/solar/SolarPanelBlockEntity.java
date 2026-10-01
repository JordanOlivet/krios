package io.lakio.krios.industry.machine.solar;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.network.EnergySink;
import io.lakio.krios.industry.network.EnergyPort;
import io.lakio.krios.industry.network.FlowPorts;
import io.lakio.krios.industry.network.Flow;
import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkManager;
import io.lakio.krios.industry.network.NetworkNode;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Passive daylight producer (design 2026-10-01). LIT = producing (day +
 * sees sky + someone to feed); only a LIT panel ticks. Unlit, it keeps a
 * slow scheduled-wake chain (ADR-017) asking "can I produce?" — rather
 * than computing dawn: scheduled ticks count REAL ticks, so {@code /time
 * set}, bed skips and {@code doDaylightCycle} would strand a computed
 * wake, while the poll self-heals them all within ~5 s for ~120 checks a
 * night. Rain halves the rate but never zeroes it (no weather
 * transitions to track — Mk I only, higher tiers will be rain-immune).
 * v1 scope: no GUI, no face config — a pure producer.
 */
public class SolarPanelBlockEntity extends BlockEntity
        implements NetworkNode, EnergyPort, io.lakio.krios.industry.machine.EnergyStats {

    /** Banks its nights (user call): produces less than a generator, stores more. */
    public static final int CAPACITY = 100_000;
    /** Park-ratio frame: one panel = two induction furnaces while the sun shines. */
    public static final int RATE_CLEAR = 8;
    public static final int RATE_RAIN = 4;
    public static final int MAX_EXTRACT_PER_TICK = 400;
    /** Unlit poll cadence (ADR-017): dawn, cleared skies, freed buffer — all within ~5 s. */
    public static final int POLL_INTERVAL_TICKS = 100;

    private final EnergyBuffer energy = new EnergyBuffer(CAPACITY, MAX_EXTRACT_PER_TICK);
    /** Live output rate, for diagnostics; 0 while dark or saturated. */
    private int currentRate;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state);
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

    // --- Production ---

    /** 0 at night or buried; halved under rain AT THIS POSITION (deserts stay dry). */
    private int productionRate(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        if (!level.isDay() || !level.canSeeSky(above)) {
            return 0;
        }
        return level.isRainingAt(above) ? RATE_RAIN : RATE_CLEAR;
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
     * offline — only the producer is ever awake (ADR-012).
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

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        energy.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.load(tag);
    }
}
