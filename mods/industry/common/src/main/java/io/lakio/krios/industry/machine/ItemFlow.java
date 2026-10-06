package io.lakio.krios.industry.machine;

import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.Flow;
import io.lakio.krios.industry.network.NetworkNode;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Machine item movement (revised 2026-10-01): two LOCAL channels only.
 * The hopper-like adjacency flow streams outputs into touching machines
 * (both face contracts respected), and MANUAL item faces exchange with
 * touching passive containers through {@link Services#ITEMS} — pulls ride
 * scheduled wake-ups (ADR-017), never a ticker. Anything beyond contact —
 * cable-borne routing, remote inventories — is krios_storage territory:
 * a network-wide first pass was rejected in play (a freshly placed
 * machine got filled from across the factory; see the design doc).
 */
public final class ItemFlow {

    /** Cadence of the scheduled wake servicing manual item faces (ADR-017). */
    public static final int WAKE_INTERVAL_TICKS = 30;

    private static final Direction[] DIRECTIONS = Direction.values();

    private ItemFlow() {
    }

    // --- Producer side: manual containers, then touching machines ---

    /** Routes the source's output slot; call sites are producer-driven. */
    public static void pushOutput(Level level, BlockPos pos, WorldlyContainer source,
            int outputSlot, FaceConfig config) {
        if (source.getItem(outputSlot).isEmpty()) {
            return;
        }
        pushToPassiveContacts(level, pos, source, outputSlot, config);
        pushToAdjacentMachines(level, pos, source, outputSlot, config);
    }

    /** Manual item-out faces feed touching passive containers. */
    private static void pushToPassiveContacts(Level level, BlockPos pos, WorldlyContainer source,
            int outputSlot, FaceConfig config) {
        for (Direction dir : DIRECTIONS) {
            ItemStack out = source.getItem(outputSlot);
            if (out.isEmpty()) {
                return;
            }
            if (!config.isManual(dir) || !config.allows(dir, Flow.ITEM, true)) {
                continue;
            }
            BlockPos targetPos = pos.relative(dir);
            if (!level.isLoaded(targetPos)
                    || level.getBlockEntity(targetPos) instanceof NetworkNode) {
                continue;
            }
            ItemStack rest = Services.ITEMS.insert(level, targetPos, dir.getOpposite(), out);
            if (rest.getCount() != out.getCount()) {
                source.setItem(outputSlot, rest);
            }
        }
    }

    /**
     * Hopper-like flow into touching machines, both face contracts
     * respected, target wake riding setItem.
     */
    private static void pushToAdjacentMachines(Level level, BlockPos pos, WorldlyContainer source,
            int outputSlot, FaceConfig config) {
        for (Direction dir : DIRECTIONS) {
            if (config.isManual(dir) && !config.allows(dir, Flow.ITEM, true)) {
                continue;
            }
            if (!(level.getBlockEntity(pos.relative(dir)) instanceof WorldlyContainer target)
                    || !(target instanceof NetworkNode)) {
                continue;
            }
            ItemStack out = source.getItem(outputSlot);
            if (out.isEmpty()) {
                return;
            }
            int moved = insertThroughFace(target, out, out.getCount(), dir.getOpposite());
            if (moved > 0) {
                out.shrink(moved);
                source.setItem(outputSlot, out);
            }
        }
    }

    /**
     * Merges copies of {@code stack} (never mutated) into the slots the
     * target exposes through {@code from} — one item per round, so column
     * election (fill-first/balance) re-runs as slots fill and both modes
     * keep their exact hopper granularity. Returns the count moved.
     */
    private static int insertThroughFace(WorldlyContainer target, ItemStack stack, int max,
            Direction from) {
        int moved = 0;
        boolean progress = true;
        while (moved < max && progress) {
            progress = false;
            for (int slot : target.getSlotsForFace(from)) {
                if (!target.canPlaceItemThroughFace(slot, stack, from)) {
                    continue;
                }
                ItemStack existing = target.getItem(slot);
                if (existing.isEmpty()) {
                    target.setItem(slot, stack.copyWithCount(1));
                } else if (ItemStack.isSameItemSameComponents(existing, stack)
                        && existing.getCount() < existing.getMaxStackSize()) {
                    existing.grow(1);
                    // Through setItem so the target's wake logic runs.
                    target.setItem(slot, existing);
                } else {
                    continue;
                }
                moved++;
                progress = true;
                break;
            }
        }
        return moved;
    }

    // --- Consumer side: scheduled pulls from passive containers ---

    /** Any manual item face, either direction: the wake chain stays alive. */
    public static boolean hasManualItemFace(FaceConfig config) {
        for (Direction dir : DIRECTIONS) {
            if (config.isManual(dir)
                    && (config.allows(dir, Flow.ITEM, false)
                            || config.allows(dir, Flow.ITEM, true))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the wake chain alive (ADR-017): schedules the next wake while
     * a manual item face exists. Entered from player actions (face edits,
     * neighbor changes) and from the wake itself — never from chunk load;
     * pending block ticks persist in chunk saves and cover reloads.
     */
    public static void scheduleWake(Level level, BlockPos pos, FaceConfig config) {
        if (level == null || level.isClientSide || !hasManualItemFace(config)) {
            return;
        }
        Block block = level.getBlockState(pos).getBlock();
        if (!level.getBlockTicks().hasScheduledTick(pos, block)) {
            level.scheduleTick(pos, block, WAKE_INTERVAL_TICKS);
        }
    }

    /**
     * One wake's pull: the first manual item-in face with a matching
     * passive neighbor yields one batch of one item kind, inserted through
     * that face's own discipline. Returns true if anything moved.
     */
    public static boolean pullIntoMachine(Level level, BlockPos pos, WorldlyContainer target,
            FaceConfig config, int max) {
        for (Direction dir : DIRECTIONS) {
            if (!config.isManual(dir) || !config.allows(dir, Flow.ITEM, false)) {
                continue;
            }
            BlockPos sourcePos = pos.relative(dir);
            if (!level.isLoaded(sourcePos)
                    || level.getBlockEntity(sourcePos) instanceof NetworkNode) {
                continue;
            }
            Direction face = dir;
            ItemStack batch = Services.ITEMS.extract(level, sourcePos, dir.getOpposite(),
                    stack -> wouldAcceptThroughFace(target, stack, face), max);
            if (batch.isEmpty()) {
                continue;
            }
            int inserted = insertThroughFace(target, batch, batch.getCount(), dir);
            batch.shrink(inserted);
            returnOrDrop(level, pos, sourcePos, dir, batch);
            if (inserted > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * One wake's pull for single-slot Containers (reactor fuel): manual
     * item-in faces poll passive neighbors for what the slot accepts.
     */
    public static boolean pullIntoSlot(Level level, BlockPos pos, Container target, int slot,
            FaceConfig config, int max) {
        ItemStack current = target.getItem(slot);
        if (!current.isEmpty() && current.getCount() >= current.getMaxStackSize()) {
            return false;
        }
        int want = current.isEmpty() ? max
                : Math.min(max, current.getMaxStackSize() - current.getCount());
        for (Direction dir : DIRECTIONS) {
            if (!config.isManual(dir) || !config.allows(dir, Flow.ITEM, false)) {
                continue;
            }
            BlockPos sourcePos = pos.relative(dir);
            if (!level.isLoaded(sourcePos)
                    || level.getBlockEntity(sourcePos) instanceof NetworkNode) {
                continue;
            }
            ItemStack batch = Services.ITEMS.extract(level, sourcePos, dir.getOpposite(),
                    stack -> target.canPlaceItem(slot, stack)
                            && (current.isEmpty()
                                    || ItemStack.isSameItemSameComponents(current, stack)),
                    want);
            if (batch.isEmpty()) {
                continue;
            }
            if (current.isEmpty()) {
                int take = Math.min(batch.getCount(), batch.getMaxStackSize());
                target.setItem(slot, batch.copyWithCount(take));
                batch.shrink(take);
            } else {
                current.grow(batch.getCount());
                target.setItem(slot, current);
                batch.setCount(0);
            }
            returnOrDrop(level, pos, sourcePos, dir, batch);
            return true;
        }
        return false;
    }

    private static boolean wouldAcceptThroughFace(WorldlyContainer target, ItemStack stack,
            Direction face) {
        for (int slot : target.getSlotsForFace(face)) {
            if (target.canPlaceItemThroughFace(slot, stack, face)) {
                return true;
            }
        }
        return false;
    }

    /** Never void an item: the leftover goes back, or drops in-world. */
    private static void returnOrDrop(Level level, BlockPos machinePos, BlockPos sourcePos,
            Direction dir, ItemStack leftover) {
        if (leftover.isEmpty()) {
            return;
        }
        ItemStack rest = Services.ITEMS.insert(level, sourcePos, dir.getOpposite(), leftover);
        if (!rest.isEmpty()) {
            Containers.dropItemStack(level, machinePos.getX() + 0.5, machinePos.getY() + 1.0,
                    machinePos.getZ() + 0.5, rest);
        }
    }
}
