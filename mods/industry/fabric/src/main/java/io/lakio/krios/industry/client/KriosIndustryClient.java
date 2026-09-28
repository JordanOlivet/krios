package io.lakio.krios.industry.client;

import io.lakio.krios.industry.client.render.BlueprintRenderer;
import io.lakio.krios.industry.client.render.EmissiveOverlayModel;
import io.lakio.krios.industry.client.render.KoiosCoreRenderer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import io.lakio.krios.industry.client.screen.CalibratorScreen;
import io.lakio.krios.industry.client.screen.CrusherScreen;
import io.lakio.krios.industry.client.screen.FurnaceScreen;
import io.lakio.krios.industry.client.screen.GeneratorScreen;
import io.lakio.krios.industry.client.screen.KoiosCoreScreen;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;

public class KriosIndustryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.GENERATOR.get(), GeneratorScreen::new);
        MenuScreens.register(ModMenus.CRUSHER.get(), CrusherScreen::new);
        MenuScreens.register(ModMenus.FURNACE.get(), FurnaceScreen::new);
        MenuScreens.register(ModMenus.KOIOS_CORE.get(), KoiosCoreScreen::new);
        MenuScreens.register(ModMenus.CALIBRATOR.get(), CalibratorScreen::new);
        BlockEntityRenderers.register(ModBlockEntities.KOIOS_CORE.get(), KoiosCoreRenderer::new);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
                BlueprintRenderer.render(context.matrixStack(), context.tickCounter().getGameTimeDeltaPartialTick(false)));
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
