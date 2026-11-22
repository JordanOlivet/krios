package io.lakio.krios.core.client;

import io.lakio.krios.core.Constants;
import io.lakio.krios.core.client.render.BlueprintRenderer;
import io.lakio.krios.core.client.render.FaceOverlayRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Game-bus client hooks (the mod-bus registrations live in KriosCoreClient). */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class KriosCoreClientGame {

    private KriosCoreClientGame() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            BlueprintRenderer.render(event.getPoseStack(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
            FaceOverlayRenderer.render(event.getPoseStack(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }
}
