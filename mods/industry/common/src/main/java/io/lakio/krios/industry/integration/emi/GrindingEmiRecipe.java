package io.lakio.krios.industry.integration.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.lakio.krios.industry.integration.ViewerText;
import io.lakio.krios.industry.machine.grinder.GrindingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/** One Fractal Grinder recipe, laid out like the JEI category. */
public class GrindingEmiRecipe extends BasicEmiRecipe {

    private static final int WIDTH = 126;
    private static final int HEIGHT = 28;
    private static final int TEXT_X = 90;
    /** White with a shadow, as EMI's own cooking recipes write. */
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final GrindingRecipe recipe;

    public GrindingEmiRecipe(EmiRecipeCategory category, RecipeHolder<GrindingRecipe> holder) {
        super(category, holder.id(), WIDTH, HEIGHT);
        this.recipe = holder.value();
        inputs.add(EmiIngredient.of(recipe.ingredient()));
        outputs.add(EmiStack.of(recipe.result()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 0, 5);
        // The arrow fills over the operation's real duration (50 ms a tick).
        widgets.addFillingArrow(26, 6, recipe.time() * 50);
        widgets.addSlot(outputs.get(0), 56, 1)
                .large(true)
                .recipeContext(this)
                .appendTooltip(ViewerText.grindingYield());
        widgets.addText(ViewerText.energy(recipe.energy()), TEXT_X, 4, TEXT_COLOR, true);
        widgets.addText(ViewerText.time(recipe.time()), TEXT_X, 15, TEXT_COLOR, true);
    }
}
