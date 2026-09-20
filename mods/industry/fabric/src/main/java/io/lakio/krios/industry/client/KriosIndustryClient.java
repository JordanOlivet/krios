package io.lakio.krios.industry.client;

import io.lakio.krios.industry.client.screen.CrusherScreen;
import io.lakio.krios.industry.client.screen.GeneratorScreen;
import io.lakio.krios.industry.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class KriosIndustryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.GENERATOR.get(), GeneratorScreen::new);
        MenuScreens.register(ModMenus.CRUSHER.get(), CrusherScreen::new);
    }
}
