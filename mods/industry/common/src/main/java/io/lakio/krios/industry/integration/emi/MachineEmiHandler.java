package io.lakio.krios.industry.integration.emi;

import java.util.List;
import java.util.function.Predicate;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import io.lakio.krios.industry.integration.MachineTransfer;
import io.lakio.krios.industry.machine.MachineMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * EMI's fill button for a machine: the recipe's one input goes from the
 * player's inventory into the machine's inputs, one item on a click, all
 * that fit on a shift-click. {@link MachineTransfer} does the moving, with
 * vanilla clicks, so the server needs no EMI.
 */
public class MachineEmiHandler<T extends AbstractContainerMenu & MachineMenu> implements EmiRecipeHandler<T> {

    private final EmiRecipeCategory category;

    public MachineEmiHandler(EmiRecipeCategory category) {
        this.category = category;
    }

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
        return new EmiPlayerInventory(screen.getMenu().slots.stream()
                .filter(slot -> slot.container instanceof Inventory)
                .map(Slot::getItem)
                .map(EmiStack::of)
                .toList());
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe.getCategory() == category && input(recipe) != null;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
        return problem(recipe, context) == MachineTransfer.Problem.NONE;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
        EmiIngredient input = input(recipe);
        return input != null
                && MachineTransfer.move(context.getScreenHandler(), accepted(input), context.getAmount()) > 0;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<T> context) {
        MachineTransfer.Problem problem = problem(recipe, context);
        if (problem == MachineTransfer.Problem.NO_ROOM || problem == MachineTransfer.Problem.CURSOR_HELD) {
            return List.of(ClientTooltipComponent.create(MachineTransfer.message(problem).getVisualOrderText()));
        }
        // Nothing to say, or EMI's own "not enough ingredients".
        return EmiRecipeHandler.super.getTooltip(recipe, context);
    }

    private MachineTransfer.Problem problem(EmiRecipe recipe, EmiCraftContext<T> context) {
        EmiIngredient input = input(recipe);
        return input == null ? MachineTransfer.Problem.MISSING
                : MachineTransfer.check(context.getScreenHandler(), accepted(input));
    }

    /** The recipe's one input, or null when it is not a one-input machine recipe. */
    private static EmiIngredient input(EmiRecipe recipe) {
        EmiIngredient found = null;
        for (EmiIngredient input : recipe.getInputs()) {
            if (!input.isEmpty()) {
                if (found != null) {
                    return null;
                }
                found = input;
            }
        }
        return found;
    }

    private static Predicate<ItemStack> accepted(EmiIngredient input) {
        return MachineTransfer.anyOf(input.getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(stack -> !stack.isEmpty())
                .toList());
    }
}
