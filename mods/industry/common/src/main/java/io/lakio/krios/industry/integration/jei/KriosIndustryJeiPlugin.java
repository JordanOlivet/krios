package io.lakio.krios.industry.integration.jei;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.grinder.GrindingRecipe;
import io.lakio.krios.industry.registry.ModBlocks;
import io.lakio.krios.industry.registry.ModItems;
import io.lakio.krios.industry.registry.ModRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI integration of Industry (optional, client-only; found by its
 * annotation on NeoForge and by the {@code jei_mod_plugin} entrypoint on
 * Fabric): the grinding category, and the machines as catalysts of the
 * vanilla categories they run (smelting for the Plasma Forge, fuels for
 * the Thermal Reactor). The terminals' off-frame areas are core's part.
 */
@JeiPlugin
public class KriosIndustryJeiPlugin implements IModPlugin {

    public static final RecipeType<RecipeHolder<GrindingRecipe>> GRINDING =
            RecipeType.createRecipeHolderType(Constants.id("grinding"));

    private static final ResourceLocation UID = Constants.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new GrindingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(GRINDING,
                    level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.GRINDING.get()));
        }
        // Copying an Engram is a special crafting recipe viewers cannot list.
        registration.addIngredientInfo(ModItems.ENGRAM.get(),
                Component.translatable("gui.krios_industry.viewer.engram_info"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(GRINDING,
                ModBlocks.FRACTAL_GRINDER.get(), ModBlocks.FRACTAL_GRINDER_TIER2.get());
        registration.addRecipeCatalysts(RecipeTypes.SMELTING,
                ModBlocks.PLASMA_FORGE.get(), ModBlocks.PLASMA_FORGE_TIER2.get());
        registration.addRecipeCatalysts(RecipeTypes.FUELING,
                ModBlocks.THERMAL_REACTOR.get(), ModBlocks.THERMAL_REACTOR_TIER2.get());
    }
}
