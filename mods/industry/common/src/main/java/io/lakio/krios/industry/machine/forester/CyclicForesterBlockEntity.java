package io.lakio.krios.industry.machine.forester;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.ZoneGeometry;
import io.lakio.krios.industry.machine.zone.PlantingZoneBlockEntity;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Cyclic Forester (design 2026-10-08): plants saplings on a grid (every
 * other cell both ways), fells grown trees PROGRESSIVELY — a detected tree
 * becomes a persisted top-down queue of logs and natural leaves, cut a few
 * blocks per wake — and routes saplings back to its plant slots. Pacing,
 * routing, energy and zone are the shared {@link PlantingZoneBlockEntity}.
 *
 * <p>Tree detection: a log at zone level on dirt-like soil, anywhere in the
 * zone (a pre-existing tree is cleared too; only PLANTING follows the
 * grid). Logs through 26-connectivity within hard bounds (height, the
 * zone plus a margin — merged neighbors are felled whole); leaves only when
 * natural (non-persistent) and only those whose nearest tree is this one
 * (BFS steps == vanilla DISTANCE), so shared canopies stay with their
 * neighbor. No anti-house guard (user call after play): placing the
 * machine is the player's responsibility, a leafless log stump is felled.
 */
public class CyclicForesterBlockEntity extends PlantingZoneBlockEntity {

    private static final int MAX_HEIGHT = 32;
    /**
     * Horizontal reach beyond the zone edge. Bounded by the ZONE, not by a
     * radius around the first stump: neighboring big oaks merge through
     * their branches, and a radius measured from one stump left the
     * other's far branches floating (seen in play, 2026-10-08).
     */
    private static final int MARGIN = 8;
    /** Several merged big oaks must fit, or the count cuts a cluster in two. */
    private static final int MAX_LOGS = 1024;
    private static final int MAX_LEAVES = 1024;
    /** Vanilla leaves die beyond distance 6: nothing natural lies further. */
    private static final int LEAF_REACH = 6;

    /** Blocks of the tree being felled, top-down (persisted). */
    private final ArrayDeque<BlockPos> fellQueue = new ArrayDeque<>();
    /** KOIOS key of the tree being felled (its base log). */
    private String fellKey = "";

    public CyclicForesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CYCLIC_FORESTER.get(), pos, state);
    }

    // --- Saplings ---

    /**
     * The sapling state an item plants. Dark oak is refused: it only grows
     * as a 2x2, deferred with the giant trees (design 2026-10-08).
     */
    @Nullable
    public static BlockState plantFor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof SaplingBlock
                && item.getBlock() != Blocks.DARK_OAK_SAPLING) {
            return item.getBlock().defaultBlockState();
        }
        return null;
    }

    public static boolean isPlantable(ItemStack stack) {
        return plantFor(stack) != null;
    }

    @Override
    @Nullable
    protected BlockState plantableState(ItemStack stack) {
        return plantFor(stack);
    }

    @Override
    protected boolean isGrowing(BlockState state) {
        return state.getBlock() instanceof SaplingBlock;
    }

    @Override
    protected int operationXp() {
        return KoiosBalance.FELL_BASE_XP;
    }

    // --- Cells ---

    @Override
    protected CellResult workCell(ServerLevel level, BlockPos pos, int row, int col, Work work) {
        BlockState state = level.getBlockState(pos);
        BlockState soil = level.getBlockState(pos.below());
        if (state.is(BlockTags.LOGS)) {
            if (!soil.is(BlockTags.DIRT)) {
                return CellResult.SKIP;
            }
            markWorkable();
            return detectTree(level, pos) ? CellResult.ACTED : CellResult.SKIP;
        }
        // Planting grid: every other cell in both directions.
        if (row % 2 != 0 || col % 2 != 0) {
            return CellResult.SKIP;
        }
        if (state.getBlock() instanceof SaplingBlock) {
            markWorkable();
            return CellResult.SKIP;
        }
        if (!(state.isAir() || state.canBeReplaced()) || !soil.is(BlockTags.DIRT)) {
            return CellResult.SKIP;
        }
        markWorkable();
        int slot = plantSlotFor(level, pos);
        if (slot < 0) {
            markNoPlants();
            return CellResult.SKIP;
        }
        if (!energy.consume(cost(level, KoiosBalance.PLANT_ENERGY, work.efficiencyPermille))) {
            return CellResult.STARVED;
        }
        plant(level, pos, slot, SoundEvents.GRASS_PLACE);
        return CellResult.ACTED;
    }

    /** Queues the tree (or bare logs) standing on {@code base}. */
    private boolean detectTree(ServerLevel level, BlockPos base) {
        // Logs: 26-connected (acacia branches), bounded; on the base layer
        // only the base itself, so a neighbor's trunk is never pulled in.
        BlockPos zoneMin = ZoneGeometry.min(worldPosition, zoneDirection(), zoneEdgeForDisplay());
        BlockPos zoneMax = ZoneGeometry.max(worldPosition, zoneDirection(), zoneEdgeForDisplay());
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        logs.add(base);
        seen.add(base);
        open.add(base);
        while (!open.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos p = open.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (!seen.add(n) || n.getY() <= base.getY()
                                || n.getY() - base.getY() > MAX_HEIGHT
                                || n.getX() < zoneMin.getX() - MARGIN || n.getX() > zoneMax.getX() + MARGIN
                                || n.getZ() < zoneMin.getZ() - MARGIN || n.getZ() > zoneMax.getZ() + MARGIN
                                || !level.isLoaded(n) || !level.getBlockState(n).is(BlockTags.LOGS)) {
                            continue;
                        }
                        logs.add(n);
                        open.add(n);
                    }
                }
            }
        }
        // Leaves: BFS through natural leaves from every log. Taken only when
        // this tree is (one of) the nearest — steps == vanilla DISTANCE.
        List<BlockPos> leaves = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>(logs);
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>(logs);
        ArrayDeque<Integer> steps = new ArrayDeque<>();
        for (int i = 0; i < logs.size(); i++) {
            steps.add(0);
        }
        while (!frontier.isEmpty() && leaves.size() < MAX_LEAVES) {
            BlockPos p = frontier.poll();
            int d = steps.poll();
            if (d >= LEAF_REACH) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (!visited.add(n) || !level.isLoaded(n)) {
                    continue;
                }
                BlockState s = level.getBlockState(n);
                if (!(s.getBlock() instanceof LeavesBlock) || s.getValue(LeavesBlock.PERSISTENT)
                        || s.getValue(LeavesBlock.DISTANCE) != d + 1) {
                    continue;
                }
                leaves.add(n);
                frontier.add(n);
                steps.add(d + 1);
            }
        }
        // Top-down; leaves before logs on a layer, so the canopy melts
        // before the logs holding it disappear (no decay drops on the ground).
        List<BlockPos> all = new ArrayList<>(leaves);
        all.addAll(logs);
        Set<BlockPos> logSet = new HashSet<>(logs);
        all.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()
                .thenComparingInt(p -> logSet.contains(p) ? 1 : 0));
        fellQueue.clear();
        fellQueue.addAll(all);
        fellKey = BuiltInRegistries.BLOCK.getKey(level.getBlockState(base).getBlock()).toString();
        setChanged();
        return true;
    }

    // --- Progressive felling ---

    @Override
    protected boolean workQueuedPending() {
        return !fellQueue.isEmpty();
    }

    /** Felling pace: one layer every FELL_LAYER_DELAY ticks, faster per tier. */
    @Override
    protected int activeDelay() {
        return fellQueue.isEmpty() ? super.activeDelay()
                : Math.max(1, KoiosBalance.FELL_LAYER_DELAY_TICKS
                        / MachineTiers.speedFactor(effectiveTier()));
    }

    @Override
    protected boolean workQueued(ServerLevel level, Work work) {
        if (fellQueue.isEmpty()) {
            return false;
        }
        // One Y layer per wake (user call after play): the tree visibly
        // shrinks from the top, layer by layer, at the felling pace below.
        int layer = fellQueue.peekFirst().getY();
        int cut = 0;
        while (!fellQueue.isEmpty() && fellQueue.peekFirst().getY() == layer) {
            BlockPos p = fellQueue.peekFirst();
            BlockState s = level.isLoaded(p) ? level.getBlockState(p) : Blocks.AIR.defaultBlockState();
            boolean log = s.is(BlockTags.LOGS);
            boolean leaf = s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT);
            if (!log && !leaf) {
                // Mined meanwhile (or unloaded): nothing to cut there.
                fellQueue.pollFirst();
                continue;
            }
            List<ItemStack> drops = new ArrayList<>(Block.getDrops(s, level, p, null));
            int quarters = yieldQuarters;
            if (log) {
                // Yield modules ride the logs (+25% per point, quarter carry).
                quarters += work.yieldPoints;
                for (int bonus = quarters / 4; bonus > 0; bonus--) {
                    drops.addAll(Block.getDrops(s, level, p, null));
                }
            }
            if (!stow(drops, -1, true)) {
                work.blocked = true;
                break;
            }
            int blockCost = cost(level, log ? KoiosBalance.LOG_CUT_ENERGY : KoiosBalance.LEAF_CUT_ENERGY,
                    work.efficiencyPermille);
            if (!energy.consume(blockCost)) {
                work.starved = true;
                break;
            }
            if (log) {
                yieldQuarters = quarters % 4;
            }
            stow(drops, -1, false);
            level.levelEvent(2001, p, Block.getId(s));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            fellQueue.pollFirst();
            cut++;
        }
        work.actions += cut;
        if (fellQueue.isEmpty()) {
            work.report("fell:" + fellKey);
            setChanged();
            return false;
        }
        setChanged();
        return true;
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        long[] queue = new long[fellQueue.size()];
        int i = 0;
        for (BlockPos p : fellQueue) {
            queue[i++] = p.asLong();
        }
        tag.putLongArray("FellQueue", queue);
        tag.putString("FellKey", fellKey);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fellQueue.clear();
        for (long packed : tag.getLongArray("FellQueue")) {
            fellQueue.add(BlockPos.of(packed));
        }
        fellKey = tag.getString("FellKey");
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.cyclic_forester");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CyclicForesterMenu(containerId, playerInventory, this, dataAccess);
    }
}
