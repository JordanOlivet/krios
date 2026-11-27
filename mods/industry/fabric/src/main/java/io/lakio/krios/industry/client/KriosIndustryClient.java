package io.lakio.krios.industry.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import io.lakio.krios.industry.client.screen.FractalGrinderScreen;
import io.lakio.krios.industry.client.screen.PlasmaForgeScreen;
import io.lakio.krios.industry.client.screen.ThermalReactorScreen;
import io.lakio.krios.industry.client.screen.PhotonArrayScreen;
import io.lakio.krios.industry.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class KriosIndustryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.THERMAL_REACTOR.get(), ThermalReactorScreen::new);
        MenuScreens.register(ModMenus.MAGMA_REACTOR.get(),
                io.lakio.krios.industry.client.screen.MagmaReactorScreen::new);
        MenuScreens.register(ModMenus.PHOTON_ARRAY.get(), PhotonArrayScreen::new);
        MenuScreens.register(ModMenus.FRACTAL_GRINDER.get(), FractalGrinderScreen::new);
        MenuScreens.register(ModMenus.PLASMA_FORGE.get(), PlasmaForgeScreen::new);
        MenuScreens.register(ModMenus.CHRONO_FARMER.get(),
                io.lakio.krios.industry.client.screen.ChronoFarmerScreen::new);
        MenuScreens.register(ModMenus.CYCLIC_FORESTER.get(),
                io.lakio.krios.industry.client.screen.CyclicForesterScreen::new);
        MenuScreens.register(ModMenus.QUANTUM_LURE.get(),
                io.lakio.krios.industry.client.screen.QuantumLureScreen::new);
        MenuScreens.register(ModMenus.BREEDING_POD.get(),
                io.lakio.krios.industry.client.screen.BreedingPodScreen::new);
        MenuScreens.register(ModMenus.HARVEST_POD.get(),
                io.lakio.krios.industry.client.screen.HarvestPodScreen::new);
        MenuScreens.register(ModMenus.PHASE_DEATH_FIELD.get(),
                io.lakio.krios.industry.client.screen.PhaseDeathFieldScreen::new);
        MenuScreens.register(ModMenus.BIO_SYNTHESIZER.get(),
                io.lakio.krios.industry.client.screen.BioSynthesizerScreen::new);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            io.lakio.krios.industry.client.render.ZoneHologramRenderer.render(context.matrixStack(),
                    context.tickCounter().getGameTimeDeltaPartialTick(false));
        });
    }
}
