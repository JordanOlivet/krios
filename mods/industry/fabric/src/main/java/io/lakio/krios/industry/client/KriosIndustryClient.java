package io.lakio.krios.industry.client;

import io.lakio.krios.industry.client.render.BlueprintRenderer;
import io.lakio.krios.industry.client.render.FaceOverlayRenderer;
import io.lakio.krios.industry.client.render.EmissiveOverlayModel;
import io.lakio.krios.industry.client.render.KoiosCoreRenderer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import io.lakio.krios.industry.client.screen.CalibratorScreen;
import io.lakio.krios.industry.client.screen.FractalGrinderScreen;
import io.lakio.krios.industry.client.screen.PlasmaForgeScreen;
import io.lakio.krios.industry.client.screen.ThermalReactorScreen;
import io.lakio.krios.industry.client.screen.KoiosCoreScreen;
import io.lakio.krios.industry.client.screen.PhotonArrayScreen;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;

public class KriosIndustryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.THERMAL_REACTOR.get(), ThermalReactorScreen::new);
        MenuScreens.register(ModMenus.PHOTON_ARRAY.get(), PhotonArrayScreen::new);
        MenuScreens.register(ModMenus.FRACTAL_GRINDER.get(), FractalGrinderScreen::new);
        MenuScreens.register(ModMenus.PLASMA_FORGE.get(), PlasmaForgeScreen::new);
        MenuScreens.register(ModMenus.KOIOS_CORE.get(), KoiosCoreScreen::new);
        MenuScreens.register(ModMenus.CALIBRATOR.get(), CalibratorScreen::new);
        BlockEntityRenderers.register(ModBlockEntities.KOIOS_CORE.get(), KoiosCoreRenderer::new);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            BlueprintRenderer.render(context.matrixStack(), context.tickCounter().getGameTimeDeltaPartialTick(false));
            FaceOverlayRenderer.render(context.matrixStack(), context.tickCounter().getGameTimeDeltaPartialTick(false));
        });
        // Emissive ore veins: NeoForge reads neoforge:face_data from the
        // model JSON; on Fabric the equivalent is this baked-model wrapper.
        ModelLoadingPlugin.register(pluginContext ->
                pluginContext.modifyModelAfterBake().register((model, context) -> {
                    ResourceLocation id = context.resourceId();
                    if (id != null && model != null && EmissiveOverlayModel.TARGETS.contains(id)) {
                        return new EmissiveOverlayModel(model);
                    }
                    return model;
                }));
    }
}
