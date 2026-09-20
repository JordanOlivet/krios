package io.lakio.krios.industry.client;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.client.screen.GeneratorScreen;
import io.lakio.krios.industry.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class KriosIndustryClient {

    private KriosIndustryClient() {
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.GENERATOR.get(), GeneratorScreen::new);
    }
}
