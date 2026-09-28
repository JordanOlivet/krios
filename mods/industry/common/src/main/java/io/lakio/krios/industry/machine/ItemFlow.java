package io.lakio.krios.industry.machine;

import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.Flow;
import io.lakio.krios.industry.network.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Hopper-like machine-to-machine item flow (design: items flow freely
 * inside the network; hoppers stay the bridge to third-party blocks). A
 * machine pushes its output into any ADJACENT mod machine that accepts it
 * through the touching faces — both face contracts respected, target wake
 * riding its setItem. v0 adjacency; the network graph takes over with the
 * transport tiers.
 */
public final class ItemFlow {

    private ItemFlow() {
    }

    /** Push the source's output slot into adjacent machine inputs. */
    public static void pushOutput(Level level, BlockPos pos, WorldlyContainer source,
            int outputSlot, FaceConfig config) {
        if (source.getItem(outputSlot).isEmpty()) {
            return;
        }
        for (Direction dir : Direction.values()) {
            if (config.isManual(dir) && !config.allows(dir, Flow.ITEM, true)) {
                continue;
            }
            if (!(level.getBlockEntity(pos.relative(dir)) instanceof WorldlyContainer target)
                    || !(target instanceof NetworkNode)) {
                continue;
            }
            Direction from = dir.getOpposite();
            for (int slot : target.getSlotsForFace(from)) {
                ItemStack out = source.getItem(outputSlot);
                if (out.isEmpty()) {
                    return;
                }
                if (!target.canPlaceItemThroughFace(slot, out, from)) {
                    continue;
                }
                ItemStack existing = target.getItem(slot);
                int move;
                if (existing.isEmpty()) {
                    move = out.getCount();
                } else if (ItemStack.isSameItemSameComponents(existing, out)) {
                    move = Math.min(out.getCount(), existing.getMaxStackSize() - existing.getCount());
                } else {
                    continue;
                }
                if (move <= 0) {
                    continue;
                }
                if (existing.isEmpty()) {
                    ItemStack moved = out.copy();
                    moved.setCount(move);
                    target.setItem(slot, moved);
                } else {
                    existing.grow(move);
                    // Through setItem so the target's wake logic runs.
                    target.setItem(slot, existing);
                }
                out.shrink(move);
                source.setItem(outputSlot, out);
            }
        }
    }
}
