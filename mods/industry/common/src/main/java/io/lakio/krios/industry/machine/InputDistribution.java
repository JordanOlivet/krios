package io.lakio.krios.industry.machine;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Input distribution across parallel process columns (user request,
 * 2026-09-30): FILL FIRST (default) tops the first column up before
 * touching the next; BALANCE elects the compatible column holding the
 * least. One elected column at a time — hoppers, machine flow and
 * shift-clicks all funnel through it, so both modes behave identically
 * whatever inserts.
 */
public final class InputDistribution {

    /**
     * The column slot the current stack should land in, or -1 when no
     * column accepts it (all full or item-incompatible).
     */
    public static int chooseColumn(Container container, int inputFirst, int columns,
            ItemStack stack, boolean balance) {
        int chosen = -1;
        int best = Integer.MAX_VALUE;
        for (int column = 0; column < columns; column++) {
            ItemStack slot = container.getItem(inputFirst + column);
            boolean fits = slot.isEmpty()
                    || (ItemStack.isSameItemSameComponents(slot, stack)
                            && slot.getCount() < slot.getMaxStackSize());
            if (!fits) {
                continue;
            }
            if (!balance) {
                return inputFirst + column;
            }
            if (slot.getCount() < best) {
                best = slot.getCount();
                chosen = inputFirst + column;
            }
        }
        return chosen;
    }

    private InputDistribution() {
    }
}
