package io.lakio.krios.industry.integration.jei;

import io.lakio.krios.industry.integration.MagmaReactorDisplay;
import io.lakio.krios.industry.integration.ViewerText;
import io.lakio.krios.industry.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * Magma Reactor: a lava bucket, or a bucket's worth of lava from a pipe or a
 * cauldron, into energy. The slots answer "what uses lava" for the item and
 * the fluid alike.
 */
public class MagmaReactorCategory extends AbstractRecipeCategory<MagmaReactorDisplay> {

    private static final int WIDTH = 126;
    private static final int HEIGHT = 28;
    private static final int TEXT_X = 48;

    private final long bucketVolume;

    public MagmaReactorCategory(IGuiHelper guiHelper, long bucketVolume) {
        super(KriosIndustryJeiPlugin.MAGMA_REACTOR,
                Component.translatable("gui.krios_industry.viewer.magma_reactor"),
                guiHelper.createDrawableItemLike(ModBlocks.MAGMA_REACTOR.get()),
                WIDTH, HEIGHT);
        this.bucketVolume = bucketVolume;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MagmaReactorDisplay recipe, IFocusGroup focuses) {
        builder.addInputSlot(1, 6)
                .setStandardSlotBackground()
                .addItemStack(new ItemStack(Items.LAVA_BUCKET))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(MagmaReactorDisplay.modules()));
        builder.addInputSlot(21, 6)
                .setStandardSlotBackground()
                .addFluidStack(Fluids.LAVA, bucketVolume)
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(MagmaReactorDisplay.modules()));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MagmaReactorDisplay recipe, IFocusGroup focuses) {
        builder.addText(ViewerText.energy(recipe.energyPerBucket()), WIDTH - TEXT_X, 10)
                .setPosition(TEXT_X, 4);
        builder.addText(recipe.rate(), WIDTH - TEXT_X, 10)
                .setPosition(TEXT_X, 15);
    }
}
