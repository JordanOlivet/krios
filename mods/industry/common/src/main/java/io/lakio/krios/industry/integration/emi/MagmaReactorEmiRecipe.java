package io.lakio.krios.industry.integration.emi;

import java.util.List;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.lakio.krios.core.platform.Services;
import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.integration.MagmaReactorDisplay;
import io.lakio.krios.industry.integration.ViewerText;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * The Magma Reactor's lava, laid out like the JEI category. One ingredient
 * holds the bucket and the fluid, so EMI lists the recipe under both and the
 * fill button still sees a one-input machine recipe.
 */
public class MagmaReactorEmiRecipe extends BasicEmiRecipe {

    private static final int WIDTH = 126;
    private static final int HEIGHT = 28;
    private static final int TEXT_X = 48;
    /** Dark, no shadow: EMI's recipe background is light (see GrindingEmiRecipe). */
    private static final int TEXT_COLOR = 0xFF404040;

    private final MagmaReactorDisplay display;

    public MagmaReactorEmiRecipe(EmiRecipeCategory category, MagmaReactorDisplay display) {
        // Synthetic recipe: EMI's convention is an id path starting with a slash.
        super(category, Constants.id("/magma_reactor/lava"), WIDTH, HEIGHT);
        this.display = display;
        // EMI counts fluids in the loader's unit: droplets on Fabric, millibuckets on NeoForge.
        long bucket = "Fabric".equals(Services.PLATFORM.getPlatformName()) ? 81_000L : 1_000L;
        inputs.add(EmiIngredient.of(List.of(EmiStack.of(Items.LAVA_BUCKET), EmiStack.of(Fluids.LAVA, bucket))));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 0, 5).appendTooltip(MagmaReactorDisplay.modules());
        widgets.addText(ViewerText.energy(display.energyPerBucket()), TEXT_X, 4, TEXT_COLOR, false);
        widgets.addText(display.rate(), TEXT_X, 15, TEXT_COLOR, false);
    }
}
