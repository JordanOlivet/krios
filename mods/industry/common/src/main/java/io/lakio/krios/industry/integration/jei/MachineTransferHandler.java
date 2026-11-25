package io.lakio.krios.industry.integration.jei;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import io.lakio.krios.industry.integration.MachineTransfer;
import io.lakio.krios.industry.machine.MachineMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * JEI's "+" button for a machine: the recipe's one input goes from the
 * player's inventory into the machine's inputs, one item on a click, all
 * that fit on a shift-click. {@link MachineTransfer} does the moving, with
 * vanilla clicks, so the server needs no JEI.
 */
public class MachineTransferHandler<C extends AbstractContainerMenu & MachineMenu, R>
        implements IRecipeTransferHandler<C, R> {

    private final Class<C> menuClass;
    private final MenuType<C> menuType;
    private final RecipeType<R> recipeType;
    private final IRecipeTransferHandlerHelper helper;

    public MachineTransferHandler(Class<C> menuClass, MenuType<C> menuType, RecipeType<R> recipeType,
            IRecipeTransferHandlerHelper helper) {
        this.menuClass = menuClass;
        this.menuType = menuType;
        this.recipeType = recipeType;
        this.helper = helper;
    }

    @Override
    public Class<? extends C> getContainerClass() {
        return menuClass;
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return Optional.of(menuType);
    }

    @Override
    public RecipeType<R> getRecipeType() {
        return recipeType;
    }

    @Override
    public IRecipeTransferError transferRecipe(C menu, R recipe, IRecipeSlotsView recipeSlots, Player player,
            boolean maxTransfer, boolean doTransfer) {
        List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .filter(view -> !view.isEmpty())
                .toList();
        if (inputs.size() != 1) {
            // Not a one-input machine recipe: no button.
            return helper.createInternalError();
        }
        Predicate<ItemStack> accepted = MachineTransfer.anyOf(inputs.get(0).getItemStacks().toList());
        MachineTransfer.Problem problem = MachineTransfer.check(menu, accepted);
        if (problem == MachineTransfer.Problem.MISSING) {
            // JEI's own wording, and the missing slot in red.
            return helper.createUserErrorForMissingSlots(
                    Component.translatable("jei.tooltip.error.recipe.transfer.missing"), inputs);
        }
        if (problem != MachineTransfer.Problem.NONE) {
            return helper.createUserErrorWithTooltip(MachineTransfer.message(problem));
        }
        if (doTransfer) {
            MachineTransfer.move(menu, accepted, maxTransfer ? Integer.MAX_VALUE : 1);
        }
        return null;
    }
}
