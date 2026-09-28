package io.lakio.krios.industry.client;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.client.render.BlueprintRenderer;
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
        }
    }
}
