package io.lakio.krios.industry.integration.emi;

import java.util.List;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.grinder.GrindingRecipe;
import io.lakio.krios.industry.registry.ModBlocks;
import io.lakio.krios.industry.registry.ModItems;
import io.lakio.krios.industry.registry.ModMenus;
import io.lakio.krios.industry.registry.ModRecipeTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * EMI integration of Industry (optional, client-only; found by its
 * annotation on NeoForge and by the {@code emi} entrypoint on Fabric).
 * Same scope as the JEI plugin: the grinding category, and the machines as
 * workstations of the vanilla categories they run. The category's name is
 * the lang key {@code emi.category.krios_industry.grinding}.
 */
@EmiEntrypoint
public class KriosIndustryEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        EmiStack grinder = EmiStack.of(ModBlocks.FRACTAL_GRINDER.get());
        EmiRecipeCategory grinding = new EmiRecipeCategory(Constants.id("grinding"), grinder);
        registry.addCategory(grinding);
        registry.addWorkstation(grinding, grinder);
        registry.addWorkstation(grinding, EmiStack.of(ModBlocks.FRACTAL_GRINDER_TIER2.get()));
        for (RecipeHolder<GrindingRecipe> holder
                : registry.getRecipeManager().getAllRecipesFor(ModRecipeTypes.GRINDING.get())) {
            registry.addRecipe(new GrindingEmiRecipe(grinding, holder));
        }

        registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, EmiStack.of(ModBlocks.PLASMA_FORGE.get()));
        registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, EmiStack.of(ModBlocks.PLASMA_FORGE_TIER2.get()));
        registry.addWorkstation(VanillaEmiRecipeCategories.FUEL, EmiStack.of(ModBlocks.THERMAL_REACTOR.get()));
        registry.addWorkstation(VanillaEmiRecipeCategories.FUEL, EmiStack.of(ModBlocks.THERMAL_REACTOR_TIER2.get()));

        // The fill button, in the machine that runs each category.
        registry.addRecipeHandler(ModMenus.FRACTAL_GRINDER.get(), new MachineEmiHandler<>(grinding));
        registry.addRecipeHandler(ModMenus.PLASMA_FORGE.get(),
                new MachineEmiHandler<>(VanillaEmiRecipeCategories.SMELTING));
        registry.addRecipeHandler(ModMenus.THERMAL_REACTOR.get(),
                new MachineEmiHandler<>(VanillaEmiRecipeCategories.FUEL));

        // Copying an Engram is a special crafting recipe viewers cannot list.
        // Synthetic recipe: EMI's convention is an id path starting with a slash.
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(ModItems.ENGRAM.get())),
                List.of(Component.translatable("gui.krios_industry.viewer.engram_info")),
                Constants.id("/info/engram")));
    }
}
