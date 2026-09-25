package io.lakio.krios.industry.client;

import io.lakio.krios.industry.client.render.KoiosCoreRenderer;
import io.lakio.krios.industry.client.screen.CalibratorScreen;
import io.lakio.krios.industry.client.screen.CrusherScreen;
import io.lakio.krios.industry.client.screen.GeneratorScreen;
import io.lakio.krios.industry.client.screen.KoiosCoreScreen;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class KriosIndustryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.GENERATOR.get(), GeneratorScreen::new);
        MenuScreens.register(ModMenus.CRUSHER.get(), CrusherScreen::new);
        MenuScreens.register(ModMenus.KOIOS_CORE.get(), KoiosCoreScreen::new);
        MenuScreens.register(ModMenus.CALIBRATOR.get(), CalibratorScreen::new);
        BlockEntityRenderers.register(ModBlockEntities.KOIOS_CORE.get(), KoiosCoreRenderer::new);
    }
}
