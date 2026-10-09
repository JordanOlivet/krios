package io.lakio.krios.industry.machine.zone;

import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.ZoneGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Planting layer of the zone machines (ADR-025) — Chrono Farmer, Cyclic
 * Forester: 3 plant slots, a cursor sweeping the zone cell by cell with
 * pass bookkeeping, Speed modules as forced random ticks, and the
 * plantables-first routing with the warned voiding toggle.
 *
 * Subclasses implement {@link #workCell} (and optionally
 * {@link #workQueued} for multi-wake jobs such as progressive felling).
 */
public abstract class PlantingZoneBlockEntity extends ZoneMachineBlockEntity {

    public static final int PLANT_SLOTS = 3;

    public static final int BUTTON_TOGGLE_VOID = 103;

    public static final int STATE_NO_PLANTS = 3;
    public static final int STATE_EMPTY_ZONE = 4;

    /** Workable cells, then the voiding toggle, after the shared data. */
    public static final int DATA_COUNT = BASE_DATA_COUNT + 2;

    /**
     * Vanilla random tick odds per block per game tick are 3/4096 (default
     * randomTickSpeed); Speed modules add pts/2 of that rate, the same
     * x(2+pts)/2 pace as every other machine. Fixed-point carry: 8192.
     */
    private static final int GROWTH_ODDS_NUM = 3;
    private static final int GROWTH_ODDS_DEN = 8192;

    /** Plantable surplus is destroyed instead of buffered (off by default). */
    private boolean voidSurplus;
    /** Next cell to scan; a pass sweeps the zone row by row. */
    private int cursor;
    private long growthCarry;

    // Pass bookkeeping, published when a full pass completes.
    private int passWorkable;
    private boolean passNoPlants;
    /** Workable cells found by the last full pass; -1 until one completes. */
    private int workableCells = -1;
    private boolean lastPassNoPlants;

    protected PlantingZoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, PLANT_SLOTS);
    }

    // --- Subclass contract ---

    /** The block state this item plants, or null when it is not a plantable we handle. */
    @Nullable
    protected abstract BlockState plantableState(ItemStack stack);

    /**
     * One zone cell at the machine's level. Call {@link #markWorkable} for
     * cells the machine can work, {@link #markNoPlants} when a cell waits
     * for a plantable the slots lack.
     */
    protected abstract CellResult workCell(ServerLevel level, BlockPos pos, int row, int col, Work work);

    /** A block Speed modules should randomly tick (growing plant). */
    protected abstract boolean isGrowing(BlockState state);

    /**
     * Multi-wake jobs, run BEFORE the zone scan (progressive felling).
     * Returns true while the job keeps the machine busy — the scan then
     * waits for the next wake.
     */
    protected boolean workQueued(ServerLevel level, Work work) {
        return false;
    }

    /** True while a multi-wake job is pending (subclass state). */
    protected boolean workQueuedPending() {
        return false;
    }

    /** Delay after a wake that acted; multi-wake jobs may set their own pace. */
    protected int activeDelay() {
        return KoiosBalance.ZONE_ACTIVE_DELAY_TICKS;
    }

    // --- Core contract ---

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return plantableState(stack) != null;
    }

    @Override
    protected void work(ServerLevel level, Work work) {
        boolean busy = workQueued(level, work);
        if (!busy && !work.stopped()) {
            scan(level, work);
        }
        if (!work.starved && work.speedPoints > 0) {
            work.starved = forceGrowth(level, work.elapsed, work.speedPoints, work.efficiencyPermille);
        }
    }

    @Override
    protected int idleState() {
        return workableCells == 0 ? STATE_EMPTY_ZONE
                : lastPassNoPlants ? STATE_NO_PLANTS
                : STATE_WAITING;
    }

    @Override
    protected int nextDelay(Work work) {
        return work.actions > 0 ? activeDelay() : KoiosBalance.ZONE_IDLE_DELAY_TICKS;
    }

    @Override
    protected void onEdgeChanged() {
        cursor = 0;
        resetPass();
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int machineData(int index) {
        return switch (index - BASE_DATA_COUNT) {
            case 0 -> workableCells;
            case 1 -> voidSurplus ? 1 : 0;
            default -> 0;
        };
    }

    @Override
    protected boolean handleMachineButton(int id) {
        if (id != BUTTON_TOGGLE_VOID) {
            return false;
        }
        voidSurplus = !voidSurplus;
        setChanged();
        // A buffer jammed with plantables may resume once they stop landing.
        kick();
        return true;
    }

    // --- Scan ---

    /** Sweeps cells from the cursor until a full pass or the action budget. */
    private void scan(ServerLevel level, Work work) {
        int edge = zoneEdgeForDisplay();
        int cells = edge * edge;
        if (cursor >= cells) {
            cursor = 0;
            resetPass();
        }
        Direction direction = zoneDirection();
        for (int scanned = 0; scanned < cells && work.actions < work.budget; scanned++) {
            BlockPos pos = ZoneGeometry.cell(worldPosition, direction, edge, cursor);
            // Zones stop at unloaded chunks — never force-load (design).
            CellResult result = level.isLoaded(pos)
                    ? workCell(level, pos, cursor / edge, cursor % edge, work) : CellResult.SKIP;
            if (result == CellResult.BLOCKED) {
                work.blocked = true;
                return;
            }
            if (result == CellResult.STARVED) {
                work.starved = true;
                return;
            }
            if (result == CellResult.ACTED) {
                work.actions++;
            }
            if (++cursor >= cells) {
                endPass();
            }
            if (workQueuedPending()) {
                // A cell started a multi-wake job: it runs next wake.
                return;
            }
        }
    }

    protected final void markWorkable() {
        passWorkable++;
    }

    protected final void markNoPlants() {
        passNoPlants = true;
    }

    private void resetPass() {
        passWorkable = 0;
        passNoPlants = false;
    }

    private void endPass() {
        workableCells = passWorkable;
        lastPassNoPlants = passNoPlants;
        cursor = 0;
        resetPass();
    }

    /**
     * Speed modules: extra random ticks on growing plants of the zone, the
     * vanilla rate scaled by pts/2. Returns true when energy ran out.
     */
    private boolean forceGrowth(ServerLevel level, int elapsed, int speedPoints, int efficiency) {
        int edge = zoneEdgeForDisplay();
        int cells = edge * edge;
        growthCarry += (long) cells * elapsed * speedPoints * GROWTH_ODDS_NUM;
        long due = growthCarry / GROWTH_ODDS_DEN;
        growthCarry -= due * GROWTH_ODDS_DEN;
        int ticks = (int) Math.min(due, KoiosBalance.ZONE_MAX_GROWTH_TICKS_PER_WAKE);
        int tickCost = cost(level, KoiosBalance.GROWTH_TICK_ENERGY, efficiency);
        Direction direction = zoneDirection();
        for (int i = 0; i < ticks; i++) {
            BlockPos pos = ZoneGeometry.cell(worldPosition, direction, edge, level.random.nextInt(cells));
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!isGrowing(state)) {
                continue;
            }
            if (!energy.consume(tickCost)) {
                return true;
            }
            state.randomTick(level, pos, level.random);
        }
        return false;
    }

    // --- Plant slots ---

    /** First plant slot whose plantable can stand at {@code pos}. */
    protected int plantSlotFor(Level level, BlockPos pos) {
        for (int slot = 0; slot < PLANT_SLOTS; slot++) {
            BlockState plant = plantableState(items.get(slot));
            if (plant != null && plant.canSurvive(level, pos)) {
                return slot;
            }
        }
        return -1;
    }

    protected int plantSlotMatching(ItemStack plantable) {
        if (plantable.isEmpty()) {
            return -1;
        }
        for (int slot = 0; slot < PLANT_SLOTS; slot++) {
            if (items.get(slot).is(plantable.getItem())) {
                return slot;
            }
        }
        return -1;
    }

    /** Plants the slot's item at {@code pos} (one item consumed). */
    protected void plant(ServerLevel level, BlockPos pos, int slot, SoundEvent sound) {
        ItemStack stack = items.get(slot);
        BlockState plant = plantableState(stack);
        stack.shrink(1);
        level.setBlock(pos, plant, 3);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    protected static boolean takeOne(List<ItemStack> drops, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return false;
        }
        for (ItemStack drop : drops) {
            if (drop.is(wanted.getItem()) && !drop.isEmpty()) {
                drop.shrink(1);
                return true;
            }
        }
        return false;
    }

    // --- Routing ---

    /**
     * Routes one batch of drops (user design 2026-10-08): everything
     * plantable tops the plant slots up first — no guessing what counts as
     * a seed — then the surplus goes to the buffer, or is destroyed when
     * the player enabled voiding (warned in the terminal: plantable
     * products are destroyed too). Everything else goes to the buffer.
     * {@code usedPlantSlot} (or -1) paid a replant from the slots.
     * Simulate first: false means the buffer cannot take the rest and
     * nothing may change.
     */
    protected boolean stow(List<ItemStack> drops, int usedPlantSlot, boolean simulate) {
        ItemStack[] plants = new ItemStack[PLANT_SLOTS];
        for (int i = 0; i < PLANT_SLOTS; i++) {
            ItemStack stack = items.get(i);
            plants[i] = simulate ? stack.copy() : stack;
        }
        ItemStack[] buffer = bufferView(simulate);
        if (usedPlantSlot >= 0) {
            plants[usedPlantSlot].shrink(1);
        }
        for (ItemStack drop : drops) {
            ItemStack rest = simulate ? drop.copy() : drop;
            if (plantableState(rest) != null) {
                rest = merge(plants, rest);
                if (voidSurplus) {
                    continue;
                }
            }
            if (!merge(buffer, rest).isEmpty()) {
                return false;
            }
        }
        if (!simulate) {
            for (int i = 0; i < PLANT_SLOTS; i++) {
                items.set(i, plants[i]);
            }
            commitBuffer(buffer);
        }
        return true;
    }

    // --- Persistence (keys unchanged since the farmer: existing worlds load) ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Cursor", cursor);
        tag.putLong("GrowthCarry", growthCarry);
        tag.putBoolean("VoidSurplusSeeds", voidSurplus);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cursor = tag.getInt("Cursor");
        growthCarry = tag.getLong("GrowthCarry");
        voidSurplus = tag.getBoolean("VoidSurplusSeeds");
    }
}
