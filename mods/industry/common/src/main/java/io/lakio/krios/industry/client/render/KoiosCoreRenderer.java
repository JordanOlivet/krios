package io.lakio.krios.industry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The AI's face, drawn on the screen side of a formed KOIOS shell: a 3x3
 * fullbright display with eyes tracking the nearest player, blinks, a boot
 * static sequence on formation, a learning pulse and a level-up flash (all
 * driven by block events — see the block entity's EVENT_ constants).
 * Placeholder art; the real look lands with the graphics pass.
 */
public class KoiosCoreRenderer implements BlockEntityRenderer<KoiosCoreBlockEntity> {

    private static final ResourceLocation TEXTURE = Constants.id("textures/block/koios_face.png");

    // Atlas regions (64x64 texture): screen bg, eye sprite, white patch, noise strip.
    private static final float ATLAS = 64F;
    private static final float SCREEN_Z = 1.505F;
    private static final int BOOT_TICKS = 50;
    private static final int PULSE_TICKS = 12;
    private static final int LEVEL_UP_TICKS = 30;

    public KoiosCoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(KoiosCoreBlockEntity core) {
        // The face is drawn 2 blocks away from the (hidden) core position.
        return true;
    }

    @Override
    public void render(KoiosCoreBlockEntity core, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!core.isFormed() || core.getScreenSide() == null) {
            return;
        }
        Level level = core.getLevel();
        if (level == null) {
            return;
        }
        Direction side = core.getScreenSide();
        float time = level.getGameTime() + partialTick;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        switch (side) {
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            default -> poseStack.mulPose(Axis.YP.rotationDegrees(-side.toYRot()));
        }
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        float bootT = core.getClientBootStart() > 0
                ? time - core.getClientBootStart() : Float.MAX_VALUE;
        if (bootT < BOOT_TICKS) {
            renderBootStatic(pose, vc, bootT);
        } else {
            renderFace(core, level, side, pose, vc, time);
        }
        poseStack.popPose();
    }

    /** Boot sequence: scrolling static noise, flickering. */
    private void renderBootStatic(PoseStack.Pose pose, VertexConsumer vc, float bootT) {
        int frame = (int) bootT;
        float v0 = (48 + (frame * 3) % 8) / ATLAS;
        float alpha = 0.55F + 0.35F * ((frame % 3) / 2F);
        quad(pose, vc, -1.5F, -1.5F, 3F, 3F, 0 / ATLAS, v0, 48 / ATLAS, v0 + 8 / ATLAS, 1, 1, 1, alpha);
    }

    private void renderFace(KoiosCoreBlockEntity core, Level level, Direction side,
            PoseStack.Pose pose, VertexConsumer vc, float time) {
        // Screen background.
        quad(pose, vc, -1.5F, -1.5F, 3F, 3F, 0, 0, 48 / ATLAS, 48 / ATLAS, 1, 1, 1, 1);

        float levelUpT = core.getClientLevelUpStart() > 0
                ? time - core.getClientLevelUpStart() : Float.MAX_VALUE;
        float pulseT = core.getClientPulseStart() > 0
                ? time - core.getClientPulseStart() : Float.MAX_VALUE;
        boolean celebrating = levelUpT < LEVEL_UP_TICKS;

        // Eyes track the nearest player (horizontal screens only).
        float lookU = 0;
        float lookV = 0;
        BlockPos corePos = core.getBlockPos();
        if (side.getAxis().isHorizontal()) {
            double cx = corePos.getX() + 0.5 + side.getStepX() * 1.5;
            double cy = corePos.getY() + 0.5;
            double cz = corePos.getZ() + 0.5 + side.getStepZ() * 1.5;
            Player watcher = level.getNearestPlayer(cx, cy, cz, 12, false);
            if (watcher != null) {
                Direction uDir = side.getCounterClockWise();
                double du = (watcher.getX() - cx) * uDir.getStepX() + (watcher.getZ() - cz) * uDir.getStepZ();
                double dv = watcher.getEyeY() - cy;
                lookU = Mth.clamp((float) du / 6F, -1F, 1F) * 0.12F;
                lookV = Mth.clamp((float) dv / 6F, -1F, 1F) * 0.10F;
            }
        }

        // Blink: brief squash on a per-core offset cycle (suppressed while celebrating).
        long blinkPhase = (level.getGameTime() + corePos.asLong() % 41 + 41) % 87;
        float eyeScale = celebrating ? 1.3F : (blinkPhase < 4 ? 0.15F : 1F);

        float eyeW = 0.55F;
        float eyeH = 0.55F * eyeScale;
        for (int i = -1; i <= 1; i += 2) {
            float ex = i * 0.55F - eyeW / 2 + lookU;
            float ey = 0.35F - eyeH / 2 + lookV;
            quad(pose, vc, ex, ey, eyeW, eyeH, 48 / ATLAS, 0, 56 / ATLAS, 8 / ATLAS, 1, 1, 1, 1);
            if (eyeScale > 0.5F) {
                // Pupil: dark tinted white patch, pushed further by the look.
                quad(pose, vc, ex + eyeW / 2 - 0.09F + lookU * 0.8F, ey + eyeH / 2 - 0.09F + lookV * 0.8F,
                        0.18F, 0.18F, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 0.02F, 0.12F, 0.14F, 1);
            }
        }

        // Mouth: a thin cyan line, wider while celebrating.
        float mouthW = celebrating ? 1.3F : 0.9F;
        quad(pose, vc, -mouthW / 2, -0.6F, mouthW, 0.09F,
                56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 0.27F, 0.91F, 0.94F, 0.95F);

        // Learning pulse and level-up flash overlays.
        if (pulseT < PULSE_TICKS) {
            float a = (1F - pulseT / PULSE_TICKS) * 0.25F;
            quad(pose, vc, -1.5F, -1.5F, 3F, 3F, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 0.27F, 0.91F, 0.94F, a);
        }
        if (celebrating) {
            float a = (1F - levelUpT / LEVEL_UP_TICKS) * 0.35F;
            quad(pose, vc, -1.5F, -1.5F, 3F, 3F, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 1F, 1F, 1F, a);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float w, float h,
            float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        vertex(pose, vc, x, y, u0, v1, r, g, b, a);
        vertex(pose, vc, x + w, y, u1, v1, r, g, b, a);
        vertex(pose, vc, x + w, y + h, u1, v0, r, g, b, a);
        vertex(pose, vc, x, y + h, u0, v0, r, g, b, a);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y,
            float u, float v, float r, float g, float b, float a) {
        vc.addVertex(pose, x, y, SCREEN_Z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, 0, 1);
    }
}
