package io.lakio.krios.industry.platform;

import java.util.function.Predicate;

import io.lakio.krios.industry.platform.services.IItemAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Passive-container item exchange through the NeoForge item capability
 * (vanilla containers included — NeoForge registers them itself).
 */
public class NeoForgeItemAccess implements IItemAccess {

    @Override
    public ItemStack insert(Level level, BlockPos pos, Direction face, ItemStack stack) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
        if (handler == null) {
            return stack;
        }
        return ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, Direction face,
            Predicate<ItemStack> filter, int max) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        // One single item kind per batch: the first matching slot decides,
        // later slots only top the batch up. Simulate-then-extract, the
        // hopper discipline.
        ItemStack taken = ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            int room = taken.isEmpty() ? max
                    : Math.min(max, taken.getMaxStackSize()) - taken.getCount();
            if (room <= 0) {
                break;
            }
            ItemStack sim = handler.extractItem(slot, room, true);
            if (sim.isEmpty()
                    || (taken.isEmpty() ? !filter.test(sim)
                            : !ItemStack.isSameItemSameComponents(taken, sim))) {
                continue;
            }
            ItemStack got = handler.extractItem(slot, Math.min(room, sim.getCount()), false);
            if (got.isEmpty()) {
                continue;
            }
            if (taken.isEmpty()) {
                taken = got;
            } else {
                taken.grow(got.getCount());
            }
        }
        return taken;
    }
}
