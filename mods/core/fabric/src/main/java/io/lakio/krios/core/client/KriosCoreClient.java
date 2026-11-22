package io.lakio.krios.core.client;

import io.lakio.krios.core.client.render.BlueprintRenderer;
import io.lakio.krios.core.client.render.EmissiveOverlayModel;
import io.lakio.krios.core.client.render.FaceOverlayRenderer;
import io.lakio.krios.core.client.render.KoiosCoreRenderer;
import io.lakio.krios.core.client.screen.CalibratorScreen;
import io.lakio.krios.core.client.screen.KoiosCoreScreen;
import io.lakio.krios.core.registry.ModBlockEntities;
import io.lakio.krios.core.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;

public class KriosCoreClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
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
