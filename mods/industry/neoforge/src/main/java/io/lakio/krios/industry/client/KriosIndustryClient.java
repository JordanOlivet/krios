package io.lakio.krios.industry.client;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.client.EmissiveOverlays;
import io.lakio.krios.industry.client.render.EmissiveOverlayModel;
import io.lakio.krios.industry.client.render.KoiosCoreRenderer;
import io.lakio.krios.industry.client.screen.CalibratorScreen;
import io.lakio.krios.industry.client.screen.FractalGrinderScreen;
import io.lakio.krios.industry.client.screen.PlasmaForgeScreen;
import io.lakio.krios.industry.client.screen.ThermalReactorScreen;
import io.lakio.krios.industry.client.screen.KoiosCoreScreen;
import io.lakio.krios.industry.client.screen.PhotonArrayScreen;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class KriosIndustryClient {

    private KriosIndustryClient() {
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.THERMAL_REACTOR.get(), ThermalReactorScreen::new);
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
        event.register(ModMenus.KOIOS_CORE.get(), KoiosCoreScreen::new);
        event.register(ModMenus.CALIBRATOR.get(), CalibratorScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.KOIOS_CORE.get(), KoiosCoreRenderer::new);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // Emissive ore veins: wrap the baked block (and item) models so
        // every "_glow" sprite quad renders fullbright.
        for (String path : EmissiveOverlays.BLOCKS) {
            ResourceLocation block = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
            for (String variant : new String[] {"", "inventory"}) {
                ModelResourceLocation id = new ModelResourceLocation(block, variant);
                BakedModel model = event.getModels().get(id);
                if (model != null) {
                    event.getModels().put(id, new EmissiveOverlayModel(model));
                }
            }
        }
    }
}
