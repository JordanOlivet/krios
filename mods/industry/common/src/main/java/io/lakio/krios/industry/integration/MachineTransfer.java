package io.lakio.krios.industry.integration;

import java.util.List;
import java.util.function.Predicate;

import io.lakio.krios.industry.machine.MachineMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Moves a recipe's input from the player's inventory into a machine, for
 * both recipe viewer plugins (client-only, loaded by them alone).
 *
 * <p>The move is made of ordinary slot clicks, the ones a player would do:
 * no packet of ours, and nothing asked of the server (a viewer's own
 * transfer needs the viewer installed there too). Where an item lands is
 * the menu's call, {@link MachineMenu#workInputSlot}: the columns of the
 * machine's tier and its distribution mode are honored exactly as for a
 * shift-click. The client menu predicts each click, so the next one is
 * decided on the state the previous one left.
 *
 * <p>Every machine recipe takes one item an operation, so an operation is
 * one item here.
 */
public final class MachineTransfer {

    /** Why a recipe's input cannot go into the machine. */
    public enum Problem {
        NONE,
        /** The player's inventory holds none of the accepted items. */
        MISSING,
        /** The machine's inputs are full or hold something else. */
        NO_ROOM,
        /** A stack is on the cursor: the clicks would move that one. */
        CURSOR_HELD
    }

    private MachineTransfer() {
    }

    /** What stands in the way of moving one accepted item in, if anything. */
    public static <M extends AbstractContainerMenu & MachineMenu> Problem check(M menu, Predicate<ItemStack> accepted) {
        if (!menu.getCarried().isEmpty()) {
            return Problem.CURSOR_HELD;
        }
        boolean found = false;
        for (Slot slot : menu.slots) {
            if (isSource(slot, accepted)) {
                if (menu.workInputSlot(slot.getItem()) >= 0) {
                    return Problem.NONE;
                }
                found = true;
            }
        }
        return found ? Problem.NO_ROOM : Problem.MISSING;
    }

    /**
     * Moves up to {@code operations} accepted items from the player's
     * inventory into the machine's inputs ({@link Integer#MAX_VALUE}: all
     * that fit). Returns how many went in.
     */
    public static <M extends AbstractContainerMenu & MachineMenu> int move(M menu, Predicate<ItemStack> accepted,
            int operations) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        LocalPlayer player = minecraft.player;
        if (gameMode == null || player == null || !menu.getCarried().isEmpty()) {
            return 0;
        }
        int moved = 0;
        for (Slot source : List.copyOf(menu.slots)) {
            if (moved >= operations) {
                break;
            }
            if (!isSource(source, accepted) || menu.workInputSlot(source.getItem()) < 0) {
                continue;
            }
            // The whole stack on the cursor, then onto the inputs.
            click(gameMode, player, menu, source.index, 0);
            while (moved < operations && !menu.getCarried().isEmpty()) {
                ItemStack held = menu.getCarried();
                int target = menu.workInputSlot(held);
                if (target < 0) {
                    break;
                }
                Slot input = menu.getSlot(target);
                int room = input.getMaxStackSize(held) - input.getItem().getCount();
                // Balance mode elects a column for each item.
                int count = menu.isInputBalance() ? 1
                        : Math.min(Math.min(operations - moved, held.getCount()), room);
                if (count <= 0) {
                    break;
                }
                int before = held.getCount();
                if (count == before) {
                    click(gameMode, player, menu, target, 0);
                } else {
                    for (int i = 0; i < count; i++) {
                        click(gameMode, player, menu, target, 1);
                    }
                }
                int placed = before - menu.getCarried().getCount();
                if (placed <= 0) {
                    break;
                }
                moved += placed;
            }
            // What is left goes back where it came from.
            if (!menu.getCarried().isEmpty()) {
                click(gameMode, player, menu, source.index, 0);
            }
        }
        return moved;
    }

    /** The line a viewer shows for a problem the player can fix in the machine. */
    public static Component message(Problem problem) {
        return Component.translatable(problem == Problem.CURSOR_HELD
                ? "gui.krios_industry.viewer.transfer.cursor"
                : "gui.krios_industry.viewer.transfer.no_room");
    }

    /** Matches any of a recipe slot's stacks by item, as an ingredient does. */
    public static Predicate<ItemStack> anyOf(List<ItemStack> stacks) {
        return stack -> {
            for (ItemStack candidate : stacks) {
                if (ItemStack.isSameItem(candidate, stack)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static boolean isSource(Slot slot, Predicate<ItemStack> accepted) {
        return slot.container instanceof Inventory && slot.hasItem() && accepted.test(slot.getItem());
    }

    /** One left (0) or right (1) click on a slot, sent and predicted like a player's. */
    private static void click(MultiPlayerGameMode gameMode, LocalPlayer player, AbstractContainerMenu menu,
            int slot, int button) {
        gameMode.handleInventoryMouseClick(menu.containerId, slot, button, ClickType.PICKUP, player);
    }
}
