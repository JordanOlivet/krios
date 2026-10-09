package io.lakio.krios.industry.machine.lure;

import java.util.ArrayList;
import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.ZoneGeometry;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Quantum Lure (design 2026-10-09): fishes the fixed 5x5 water zone behind
 * its screen with 1/2/4/8 parallel lines by effective tier, drawing the
 * vanilla fishing loot table. No rod (ADR-024): Lure and Luck modules
 * stand in for its enchantments.
 *
 * <p>Each line runs vanilla's wait (bite + hook), minus the Lure modules,
 * divided by the Speed pace; the machine schedules exactly one wake, for
 * the next due line (ADR-022) — no cell scan. A cell fishes with a water
 * source at Y-1 and air or a lily pad at Y; all 25 make open water
 * (treasure), fewer fish without treasure, none idles.
 */
public class QuantumLureBlockEntity extends ZoneMachineBlockEntity {

    public static final int ZONE_EDGE = 5;
    public static final int ZONE_CELLS = ZONE_EDGE * ZONE_EDGE;
    /** Tier 4 ceiling of the 1/2/4/8 line ladder. */
    public static final int MAX_LINES = 8;

    public static final int STATE_NO_WATER = 3;

    public static final int WATER_NONE = 0;
    public static final int WATER_PARTIAL = 1;
    public static final int WATER_OPEN = 2;

    // Machine data after the shared block: water status, active lines,
    // per-line progress (permille), then the 25-cell water mask (2 shorts).
    public static final int DATA_WATER = BASE_DATA_COUNT;
    public static final int DATA_LINES = BASE_DATA_COUNT + 1;
    public static final int DATA_PROGRESS = BASE_DATA_COUNT + 2;
    public static final int DATA_MASK = DATA_PROGRESS + MAX_LINES;
    public static final int DATA_COUNT = DATA_MASK + 2;

    /** The loot context's tool: an unenchanted rod (modules carry the rest). */
    private static final ItemStack ROD = new ItemStack(Items.FISHING_ROD);

    /** Line start (game time) and duration; duration 0 = not cast yet. */
    private final long[] lineStart = new long[MAX_LINES];
    private final int[] lineDuration = new int[MAX_LINES];
    /** The cast's vanilla rolls (bite, hook), kept to rescale on a module swap. */
    private final int[] lineLured = new int[MAX_LINES];
    private final int[] lineHooked = new int[MAX_LINES];

    /** Last wake's water reading (synced for the terminal). */
    private int waterMask;
    private int waterCells;
    private int linesActive;
    /** Open-water hook for the treasure predicate; never added to the world. */
    @Nullable
    private FishingHook hook;

    public QuantumLureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_LURE.get(), pos, state, 0);
    }

    // --- Zone: fixed 5x5 at every tier (user call) ---

    @Override
    protected int maxEdge() {
        return ZONE_EDGE;
    }

    @Override
    protected boolean zoneResizable() {
        return false;
    }

    /** The process axes plus the rod's enchantments as modules (ADR-024). */
    public static boolean acceptsModule(ModuleType type) {
        return type.isProcessAxis() || type == ModuleType.LURE || type == ModuleType.LUCK;
    }

    @Override
    protected int operationXp() {
        return KoiosBalance.CATCH_BASE_XP;
    }

    // --- Water ---

    /** Reads the zone: bit i set when cell i can fish. */
    private int readWater(Level level) {
        int mask = 0;
        for (int i = 0; i < ZONE_CELLS; i++) {
            BlockPos surface = ZoneGeometry.cell(worldPosition, zoneDirection(), ZONE_EDGE, i);
            if (level.isLoaded(surface) && isFishingCell(level, surface)) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /** Vanilla's open-water reading per column: source water under air (or a lily pad). */
    private static boolean isFishingCell(Level level, BlockPos surface) {
        BlockState top = level.getBlockState(surface);
        if (!top.isAir() && !top.is(Blocks.LILY_PAD)) {
            return false;
        }
        BlockPos waterPos = surface.below();
        BlockState water = level.getBlockState(waterPos);
        FluidState fluid = water.getFluidState();
        return fluid.is(FluidTags.WATER) && fluid.isSource()
                && water.getCollisionShape(level, waterPos).isEmpty();
    }

    private int waterStatus() {
        return waterCells == ZONE_CELLS ? WATER_OPEN : waterCells > 0 ? WATER_PARTIAL : WATER_NONE;
    }

    // --- Work ---

    @Override
    protected void work(ServerLevel level, Work work) {
        waterMask = readWater(level);
        waterCells = Integer.bitCount(waterMask);
        linesActive = MachineTiers.processSlots(work.effectiveTier);
        if (waterCells == 0) {
            // No water: lines reel in, they recast once water is back.
            resetLines(0);
            setChanged();
            return;
        }
        resetLines(linesActive);
        long now = level.getGameTime();
        int lurePoints = work.online ? modulePoints(ModuleType.LURE) : 0;
        int luck = work.online ? modulePoints(ModuleType.LUCK) : 0;
        boolean open = waterCells == ZONE_CELLS;
        for (int line = 0; line < linesActive && !work.stopped(); line++) {
            if (lineDuration[line] == 0) {
                cast(level, line, now, work.speedPoints, lurePoints);
                continue;
            }
            if (now < lineStart[line] + lineDuration[line]) {
                continue;
            }
            if (reel(level, work, luck, open)) {
                cast(level, line, now, work.speedPoints, lurePoints);
            }
        }
        setChanged();
    }

    /** Lines past the active count stop (tier drop, no water). */
    private void resetLines(int from) {
        for (int line = from; line < MAX_LINES; line++) {
            lineDuration[line] = 0;
        }
    }

    /**
     * Vanilla's wait — 100-600 ticks to the bite, 20-80 to the hook — minus
     * the Lure points, then the Speed pace x2/(2+pts), floored.
     */
    private void cast(ServerLevel level, int line, long now, int speedPoints, int lurePoints) {
        lineLured[line] = Mth.nextInt(level.random,
                KoiosBalance.FISH_LURED_MIN_TICKS, KoiosBalance.FISH_LURED_MAX_TICKS);
        lineHooked[line] = Mth.nextInt(level.random,
                KoiosBalance.FISH_HOOKED_MIN_TICKS, KoiosBalance.FISH_HOOKED_MAX_TICKS);
        lineStart[line] = now;
        lineDuration[line] = duration(line, speedPoints, lurePoints);
    }

    private int duration(int line, int speedPoints, int lurePoints) {
        int lured = Math.max(0, lineLured[line] - KoiosBalance.LURE_TICKS_PER_POINT * lurePoints);
        int wait = (lured + lineHooked[line]) * 2 / (2 + speedPoints);
        return Math.max(KoiosBalance.FISH_MIN_WAIT_TICKS, wait);
    }

    /**
     * A module swap applies to the casts in flight (user call after play:
     * a Speed module must show at once): each line keeps its progress
     * fraction and takes the new duration, so the gauge holds its place
     * and the remaining wait follows the new modules.
     */
    @Override
    protected void onModulesChanged() {
        if (level == null) {
            return;
        }
        boolean online = isOnline();
        int speedPoints = online ? modulePoints(ModuleType.SPEED) : 0;
        int lurePoints = online ? modulePoints(ModuleType.LURE) : 0;
        long now = level.getGameTime();
        for (int line = 0; line < MAX_LINES; line++) {
            if (lineDuration[line] <= 0) {
                continue;
            }
            long elapsed = Math.max(0, Math.min(lineDuration[line], now - lineStart[line]));
            int next = duration(line, speedPoints, lurePoints);
            lineStart[line] = now - elapsed * next / lineDuration[line];
            lineDuration[line] = next;
        }
        setChanged();
    }

    /**
     * One catch: the vanilla table plus whole extra draws from the Yield
     * quarter carry (ADR-006). Returns false — nothing changed, the line
     * stays due — on a full buffer or an empty energy buffer.
     */
    private boolean reel(ServerLevel level, Work work, int luck, boolean open) {
        int quarters = yieldQuarters + work.yieldPoints;
        List<ItemStack> catches = new ArrayList<>(draw(level, luck, open));
        for (int bonus = quarters / 4; bonus > 0; bonus--) {
            catches.addAll(draw(level, luck, open));
        }
        if (!stow(catches, true)) {
            work.blocked = true;
            return false;
        }
        if (!energy.consume(cost(level, KoiosBalance.CATCH_ENERGY, work.efficiencyPermille))) {
            work.starved = true;
            return false;
        }
        yieldQuarters = quarters % 4;
        String key = catches.isEmpty() ? "nothing"
                : BuiltInRegistries.ITEM.getKey(catches.get(0).getItem()).toString();
        stow(catches, false);
        BlockPos center = zoneCenter();
        level.playSound(null, center, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.BLOCKS, 0.4F, 1.0F);
        work.report("fish:" + key);
        work.actions++;
        return true;
    }

    /**
     * The vanilla fishing table through a real loot context: rod tool,
     * module luck, the zone's center as origin (biome-gated entries such
     * as jungle bamboo read it). Treasure is gated by vanilla's open-water
     * predicate on THIS_ENTITY: in open water we pass a detached hook
     * (open by default), otherwise none — the predicate then fails.
     */
    private List<ItemStack> draw(ServerLevel level, int luck, boolean open) {
        Vec3 origin = Vec3.atCenterOf(zoneCenter().below());
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.TOOL, ROD)
                .withLuck(luck);
        if (open) {
            if (hook == null || hook.level() != level) {
                hook = new FishingHook(EntityType.FISHING_BOBBER, level);
            }
            hook.setPos(origin);
            params.withParameter(LootContextParams.THIS_ENTITY, hook);
        }
        return level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING)
                .getRandomItems(params.create(LootContextParamSets.FISHING));
    }

    private BlockPos zoneCenter() {
        return ZoneGeometry.cell(worldPosition, zoneDirection(), ZONE_EDGE, ZONE_CELLS / 2);
    }

    // --- Pacing: one wake for the next due line ---

    @Override
    protected int nextDelay(Work work) {
        if (waterCells == 0 || work.starved || level == null) {
            return KoiosBalance.ZONE_IDLE_DELAY_TICKS;
        }
        long now = level.getGameTime();
        long next = Long.MAX_VALUE;
        for (int line = 0; line < linesActive; line++) {
            if (lineDuration[line] > 0) {
                next = Math.min(next, lineStart[line] + lineDuration[line] - now);
            }
        }
        return next == Long.MAX_VALUE ? KoiosBalance.ZONE_ACTIVE_DELAY_TICKS
                : (int) Math.max(1, Math.min(next, KoiosBalance.FISH_LURED_MAX_TICKS
                        + KoiosBalance.FISH_HOOKED_MAX_TICKS));
    }

    /** Lines in the water = working (LIT), even between catches. */
    @Override
    protected boolean isWorking(Work work) {
        return waterCells > 0 && !work.stopped();
    }

    @Override
    protected int idleState() {
        return waterCells == 0 ? STATE_NO_WATER : STATE_WAITING;
    }

    // --- Synced data ---

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int machineData(int index) {
        if (index == DATA_WATER) {
            return waterStatus();
        }
        if (index == DATA_LINES) {
            return linesActive;
        }
        if (index >= DATA_PROGRESS && index < DATA_PROGRESS + MAX_LINES) {
            return lineProgress(index - DATA_PROGRESS);
        }
        if (index == DATA_MASK) {
            return waterMask & 0xFFFF;
        }
        if (index == DATA_MASK + 1) {
            return (waterMask >> 16) & 0xFFFF;
        }
        return 0;
    }

    /**
     * Gauge fill from game time, computed when the menu syncs: the gauges
     * move smoothly although the machine only wakes on catches. A due line
     * waiting on a full buffer reads full.
     */
    private int lineProgress(int line) {
        if (level == null || line >= linesActive || lineDuration[line] <= 0) {
            return 0;
        }
        long elapsed = level.getGameTime() - lineStart[line];
        return (int) Math.max(0, Math.min(1000, elapsed * 1000 / lineDuration[line]));
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLongArray("LineStart", lineStart);
        tag.putIntArray("LineDuration", lineDuration);
        tag.putIntArray("LineLured", lineLured);
        tag.putIntArray("LineHooked", lineHooked);
        tag.putInt("WaterMask", waterMask);
        tag.putInt("Lines", linesActive);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        long[] starts = tag.getLongArray("LineStart");
        int[] durations = tag.getIntArray("LineDuration");
        int[] lured = tag.getIntArray("LineLured");
        int[] hooked = tag.getIntArray("LineHooked");
        for (int line = 0; line < MAX_LINES; line++) {
            lineStart[line] = line < starts.length ? starts[line] : 0;
            lineDuration[line] = line < durations.length ? durations[line] : 0;
            // Casts saved before the rolls were kept: their duration stands in.
            lineLured[line] = line < lured.length ? lured[line] : lineDuration[line];
            lineHooked[line] = line < hooked.length ? hooked[line] : 0;
        }
        waterMask = tag.getInt("WaterMask");
        waterCells = Integer.bitCount(waterMask);
        linesActive = tag.getInt("Lines");
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.quantum_lure");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new QuantumLureMenu(containerId, playerInventory, this, dataAccess);
    }
}
