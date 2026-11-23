package io.lakio.krios.core.integration.jei;

import java.util.List;

import io.lakio.krios.core.Constants;
import io.lakio.krios.core.client.screen.KriosTerminalScreen;
import io.lakio.krios.core.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI integration of core (optional, client-only; JEI finds this class by
 * its annotation on NeoForge and by the {@code jei_mod_plugin} entrypoint
 * on Fabric). Core's part is the chrome: every Krios terminal tells JEI
 * what it draws outside its frame, so the ingredient list never covers an
 * annex panel. Recipes of core content are plain crafting and smelting,
 * which JEI shows on its own.
 */
@JeiPlugin
public class KriosCoreJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = Constants.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Generic: applies to every subclass, the content mods' terminals included.
        registration.addGenericGuiContainerHandler(KriosTerminalScreen.class,
                new IGuiContainerHandler<KriosTerminalScreen<?>>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(KriosTerminalScreen<?> screen) {
                        return screen.extraAreas();
                    }
                });
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // The multiblock has no recipe to show: say how the core comes alive.
        registration.addIngredientInfo(ModBlocks.KOIOS_CORE.get(),
                Component.translatable("block.krios_core.koios_core.hint"));
    }
}
