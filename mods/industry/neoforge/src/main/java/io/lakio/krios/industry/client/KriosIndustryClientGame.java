package io.lakio.krios.industry.client;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.client.render.BlueprintRenderer;
import io.lakio.krios.industry.client.render.FaceOverlayRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Game-bus client hooks (the mod-bus registrations live in KriosIndustryClient). */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class KriosIndustryClientGame {

    private KriosIndustryClientGame() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            BlueprintRenderer.render(event.getPoseStack(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
            FaceOverlayRenderer.render(event.getPoseStack(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
            io.lakio.krios.industry.client.render.ZoneHologramRenderer.render(event.getPoseStack(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }
}
