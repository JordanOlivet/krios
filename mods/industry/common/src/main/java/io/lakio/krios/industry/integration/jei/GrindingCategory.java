package io.lakio.krios.industry.integration.jei;

import io.lakio.krios.industry.integration.ViewerText;
import io.lakio.krios.industry.machine.grinder.GrindingRecipe;
import io.lakio.krios.industry.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Fractal Grinder recipes: one input, one output, the operation's energy
 * and duration. The count shown is the recipe's own (a tier 1 grinder on a
 * network); the output slot's tooltip says what moves it.
 */
public class GrindingCategory extends AbstractRecipeCategory<RecipeHolder<GrindingRecipe>> {

    private static final int WIDTH = 126;
    private static final int HEIGHT = 28;
    private static final int TEXT_X = 90;

    public GrindingCategory(IGuiHelper guiHelper) {
        super(KriosIndustryJeiPlugin.GRINDING,
                Component.translatable("gui.krios_industry.viewer.grinding"),
                guiHelper.createDrawableItemLike(ModBlocks.FRACTAL_GRINDER.get()),
                WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<GrindingRecipe> holder, IFocusGroup focuses) {
        GrindingRecipe recipe = holder.value();
        builder.addInputSlot(1, 6)
                .setStandardSlotBackground()
                .addIngredients(recipe.ingredient());
        builder.addOutputSlot(61, 6)
                .setOutputSlotBackground()
                .addItemStack(recipe.result())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(ViewerText.grindingYield()));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<GrindingRecipe> holder,
            IFocusGroup focuses) {
        GrindingRecipe recipe = holder.value();
        builder.addAnimatedRecipeArrow(recipe.time()).setPosition(26, 6);
        builder.addText(ViewerText.energy(recipe.energy()), WIDTH - TEXT_X, 10)
                .setPosition(TEXT_X, 4);
        builder.addText(ViewerText.time(recipe.time()), WIDTH - TEXT_X, 10)
                .setPosition(TEXT_X, 15);
    }
}
