package io.lakio.krios.industry.client;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.client.screen.FractalGrinderScreen;
import io.lakio.krios.industry.client.screen.PlasmaForgeScreen;
import io.lakio.krios.industry.client.screen.ThermalReactorScreen;
import io.lakio.krios.industry.client.screen.PhotonArrayScreen;
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
        event.register(ModMenus.THERMAL_REACTOR.get(), ThermalReactorScreen::new);
        event.register(ModMenus.MAGMA_REACTOR.get(),
                io.lakio.krios.industry.client.screen.MagmaReactorScreen::new);
        event.register(ModMenus.PHOTON_ARRAY.get(), PhotonArrayScreen::new);
        event.register(ModMenus.FRACTAL_GRINDER.get(), FractalGrinderScreen::new);
        event.register(ModMenus.PLASMA_FORGE.get(), PlasmaForgeScreen::new);
        event.register(ModMenus.CHRONO_FARMER.get(),
                io.lakio.krios.industry.client.screen.ChronoFarmerScreen::new);
        event.register(ModMenus.CYCLIC_FORESTER.get(),
                io.lakio.krios.industry.client.screen.CyclicForesterScreen::new);
        event.register(ModMenus.QUANTUM_LURE.get(),
                io.lakio.krios.industry.client.screen.QuantumLureScreen::new);
        event.register(ModMenus.BREEDING_POD.get(),
                io.lakio.krios.industry.client.screen.BreedingPodScreen::new);
        event.register(ModMenus.HARVEST_POD.get(),
                io.lakio.krios.industry.client.screen.HarvestPodScreen::new);
        event.register(ModMenus.PHASE_DEATH_FIELD.get(),
                io.lakio.krios.industry.client.screen.PhaseDeathFieldScreen::new);
        event.register(ModMenus.BIO_SYNTHESIZER.get(),
                io.lakio.krios.industry.client.screen.BioSynthesizerScreen::new);
    }
}
