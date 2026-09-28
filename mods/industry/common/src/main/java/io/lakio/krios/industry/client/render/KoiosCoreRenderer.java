package io.lakio.krios.industry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
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
    // Each layer sits on its own depth plane: coplanar quads z-fight and the
    // translucent sort reorders them unpredictably (the "overlapping renders"
    // artifact). ~mm steps are invisible but settle depth and sort order.
    // The translucent overlays must stay BEHIND the opaque features: the
    // per-quad distance sort can draw a closer overlay before an eye, whose
    // depth test then fails and the eye vanishes during pulses. Farther than
    // the features, depth testing composites correctly in any draw order.
    private static final float Z_BG = 1.502F;
    private static final float Z_FRAME = 1.5028F;
    private static final float Z_FRAME_NODE = 1.5032F;
    private static final float Z_PULSE = 1.5035F;
    private static final float Z_STATIC = 1.504F;
    private static final float Z_FLASH = 1.5042F;
    private static final float Z_FEATURES = 1.506F;
    private static final float Z_TEXT = 1.508F;
    private static final float Z_PUPILS = 1.509F;
    private static final float TEXT_SCALE = 0.022F;
    /** The on-screen phrase line: half the fault-text size, fits the frame. */
    private static final float PHRASE_SCALE = 0.011F;
    /** Wrap width in font pixels, matching the panel's inner width. */
    private static final int TEXT_WRAP_PX = (int) (2.3F / TEXT_SCALE);
    private static final int BOOT_TICKS = 50;
    private static final int PULSE_TICKS = 12;
    private static final int LEVEL_UP_TICKS = 30;

    private final Font font;

    public KoiosCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public boolean shouldRenderOffScreen(KoiosCoreBlockEntity core) {
        // The face is drawn 2 blocks away from the (hidden) core position.
        return true;
    }

    @Override
    public void render(KoiosCoreBlockEntity core, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!core.isFormed()) {
            return;
        }
        Level level = core.getLevel();
        if (level == null) {
            return;
        }
        float time = level.getGameTime() + partialTick;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        // Monolith sculpt (art direction): emissive frame beams on every
        // shell face. The AI's full hologram body is the LAST-TIER evolution
        // of the core (design decision 2026-09-29) — not rendered yet.
        renderMonolithFrame(poseStack, vc);

        Direction side = core.getScreenSide();
        if (side != null) {
            switch (side) {
                case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
                default -> poseStack.mulPose(Axis.YP.rotationDegrees(-side.toYRot()));
            }
            PoseStack.Pose pose = poseStack.last();
            float bootT = core.getClientBootStart() > 0
                    ? time - core.getClientBootStart() : Float.MAX_VALUE;
            if (bootT < BOOT_TICKS) {
                renderBootStatic(pose, vc, bootT);
            } else if (core.isNetworkFault()) {
                renderFaultStatic(poseStack, buffers, pose, vc, time);
            } else {
                renderFace(core, level, side, poseStack, buffers, vc, time);
            }
        }
        poseStack.popPose();
    }

    /**
     * Cyan frame beams along every shell face's perimeter, brighter corner
     * nodes — the "framed monolith" read. Sits between the screen background
     * and the overlays so the face content stays untouched.
     */
    private void renderMonolithFrame(PoseStack poseStack, VertexConsumer vc) {
        for (Direction face : Direction.values()) {
            poseStack.pushPose();
            switch (face) {
                case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
                default -> poseStack.mulPose(Axis.YP.rotationDegrees(-face.toYRot()));
            }
            PoseStack.Pose pose = poseStack.last();
            float r = 0.04F;
            float g = 0.43F;
            float b = 0.53F;
            quad(pose, vc, -1.5F, 1.42F, 3F, 0.08F, Z_FRAME, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, r, g, b, 0.9F);
            quad(pose, vc, -1.5F, -1.5F, 3F, 0.08F, Z_FRAME, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, r, g, b, 0.9F);
            quad(pose, vc, -1.5F, -1.42F, 0.08F, 2.84F, Z_FRAME, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, r, g, b, 0.9F);
            quad(pose, vc, 1.42F, -1.42F, 0.08F, 2.84F, Z_FRAME, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, r, g, b, 0.9F);
            poseStack.popPose();
        }
    }

    /**
     * One core per network: a conflicted core shows old-TV static with a red
     * error panel dead center, so the fault reads in-world at a glance.
     */
    private void renderFaultStatic(PoseStack poseStack, MultiBufferSource buffers,
            PoseStack.Pose pose, VertexConsumer vc, float time) {
        int frame = (int) time;
        float v0 = (48 + (frame * 5) % 8) / ATLAS;
        float alpha = 0.6F + 0.3F * ((frame % 4) / 3F);
        quad(pose, vc, -1.5F, -1.5F, 3F, 3F, Z_STATIC, 0, v0, 48 / ATLAS, v0 + 8 / ATLAS, 1, 1, 1, alpha);
        // Activity LED goes red and blinks hard on a fault.
        quad(pose, vc, 1.19F, -1.37F, 0.10F, 0.10F, Z_FEATURES,
                56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 0.9F, 0.28F, 0.30F, frame % 10 < 5 ? 0.95F : 0.35F);
        // Red panel: bright border quad below a darker inner quad.
        quad(pose, vc, -1.35F, -0.7F, 2.7F, 1.4F, Z_FLASH,
                56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 0.85F, 0.08F, 0.08F, 0.92F);
        quad(pose, vc, -1.27F, -0.62F, 2.54F, 1.24F, Z_FEATURES,
                56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 0.30F, 0.01F, 0.01F, 0.95F);
        // Error text: title plus detail wrapped to the panel width, the
        // whole block vertically centered.
        Component title = Component.translatable("gui.krios_industry.koios.fault_title");
        List<FormattedCharSequence> detail = font.split(
                Component.translatable("gui.krios_industry.koios.fault_detail"), TEXT_WRAP_PX);
        poseStack.pushPose();
        poseStack.translate(0, 0, Z_TEXT);
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        var matrix = poseStack.last().pose();
        float y = -(10 + 4 + detail.size() * 10) / 2F;
        font.drawInBatch(title, -font.width(title) / 2F, y, 0xFFFFFFFF, false,
                matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        y += 14;
        for (FormattedCharSequence line : detail) {
            font.drawInBatch(line, -font.width(line) / 2F, y, 0xFFFF8080, false,
                    matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            y += 10;
        }
        poseStack.popPose();
    }

    /** Boot sequence: scrolling static noise, flickering. */
    private void renderBootStatic(PoseStack.Pose pose, VertexConsumer vc, float bootT) {
        int frame = (int) bootT;
        float v0 = (48 + (frame * 3) % 8) / ATLAS;
        float alpha = 0.55F + 0.35F * ((frame % 3) / 2F);
        quad(pose, vc, -1.5F, -1.5F, 3F, 3F, Z_STATIC, 0 / ATLAS, v0, 48 / ATLAS, v0 + 8 / ATLAS, 1, 1, 1, alpha);
    }

    private void renderFace(KoiosCoreBlockEntity core, Level level, Direction side,
            PoseStack poseStack, MultiBufferSource buffers, VertexConsumer vc, float time) {
        PoseStack.Pose pose = poseStack.last();
        // Screen background.
        quad(pose, vc, -1.5F, -1.5F, 3F, 3F, Z_BG, 0, 0, 48 / ATLAS, 48 / ATLAS, 1, 1, 1, 1);
        // Head frame, mirroring the terminals' video feed: the face reads as
        // one delimited head, not floating features.
        float fr = 0.19F;
        float fg = 0.85F;
        float fb = 1F;
        quad(pose, vc, -1.11F, 0.85F, 2.22F, 0.06F, Z_FRAME_NODE, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, fr, fg, fb, 0.85F);
        quad(pose, vc, -1.11F, -0.91F, 2.22F, 0.06F, Z_FRAME_NODE, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, fr, fg, fb, 0.85F);
        quad(pose, vc, -1.11F, -0.85F, 0.06F, 1.7F, Z_FRAME_NODE, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, fr, fg, fb, 0.85F);
        quad(pose, vc, 1.05F, -0.85F, 0.06F, 1.7F, Z_FRAME_NODE, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, fr, fg, fb, 0.85F);

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

        // No in-world blink: the per-frame geometry change destabilized the
        // translucent sort (quads vanished). Life comes from the tracking
        // eyes, the activity LED and the phrase line instead.
        float eyeScale = celebrating ? 1.3F : 1F;

        // Full solid eyes, like the terminals' feed (no pupils): taller than
        // wide, same 2:3 ratio as the feed's 2x3 pixel eyes.
        float eyeW = 0.40F;
        float eyeH = 0.60F * eyeScale;
        for (int i = -1; i <= 1; i += 2) {
            float ex = i * 0.55F - eyeW / 2 + lookU;
            float ey = 0.35F - eyeH / 2 + lookV;
            quad(pose, vc, ex, ey, eyeW, eyeH, Z_FEATURES, 48 / ATLAS, 0, 56 / ATLAS, 8 / ATLAS, 1, 1, 1, 1);
        }

        // Mouth: a thin cyan line, wider while celebrating.
        float mouthW = celebrating ? 1.3F : 0.9F;
        quad(pose, vc, -mouthW / 2, -0.6F, mouthW, 0.09F, Z_FEATURES,
                56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 0.19F, 0.85F, 1F, 0.95F);

        // Activity LED, bottom-right: calm ~1 Hz pulse, bright flash while a
        // learning pulse plays (blink replacement — stable geometry).
        if (pulseT < PULSE_TICKS) {
            quad(pose, vc, 1.16F, -1.40F, 0.16F, 0.16F, Z_FEATURES,
                    56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 0.62F, 0.94F, 1F, 1F);
        } else {
            float ledA = 0.45F + 0.35F * Mth.sin(time * 0.3F);
            quad(pose, vc, 1.19F, -1.37F, 0.10F, 0.10F, Z_FEATURES,
                    56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 0.19F, 0.85F, 1F, ledA);
        }


        // Learning pulse and level-up flash overlays.
        if (pulseT < PULSE_TICKS) {
            float a = (1F - pulseT / PULSE_TICKS) * 0.25F;
            quad(pose, vc, -1.5F, -1.5F, 3F, 3F, Z_PULSE, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 0.19F, 0.85F, 1F, a);
        }
        if (celebrating) {
            float a = (1F - levelUpT / LEVEL_UP_TICKS) * 0.35F;
            quad(pose, vc, -1.5F, -1.5F, 3F, 3F, Z_FLASH, 56 / ATLAS, 0, 64 / ATLAS, 8 / ATLAS, 1F, 1F, 1F, a);
        }

        // KOIOS's phrase, bottom center — one short line, fading out past a
        // few blocks. MUST stay the LAST draw of this face: drawInBatch pulls
        // other render types from the buffer source, which ends the
        // entityTranslucent batch behind our VertexConsumer.
        Component phrase = phraseFor(core, time);
        Player viewer = net.minecraft.client.Minecraft.getInstance().player;
        if (phrase != null && viewer != null) {
            double dist = Math.sqrt(viewer.distanceToSqr(
                    corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5));
            float fade = Mth.clamp((float) (1.0 - (dist - 6.0) / 3.0), 0F, 1F);
            if (fade > 0.06F) {
                poseStack.pushPose();
                poseStack.translate(0, 0, Z_TEXT);
                poseStack.scale(PHRASE_SCALE, -PHRASE_SCALE, PHRASE_SCALE);
                int color = ((int) (fade * 255) << 24) | 0x5FC8DC;
                font.drawInBatch(phrase, -font.width(phrase) / 2F, 105, color, false,
                        poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL,
                        0, LightTexture.FULL_BRIGHT);
                poseStack.popPose();
            }
        }
    }

    /**
     * KOIOS's on-screen voice: same contexts as the core terminal — low
     * diversity, near a classic level-up, else a slow learning rotation.
     */
    private static Component phraseFor(KoiosCoreBlockEntity core, float time) {
        if (core.displayDiversityPermille() < 400) {
            return Component.translatable("gui.krios_industry.koios.phrase.repetitive");
        }
        int koiosLevel = core.getKoiosLevel();
        if (koiosLevel < KoiosBalance.MAX_CLASSIC_LEVEL
                && core.getXp() >= KoiosBalance.LEVEL_XP[koiosLevel + 1] * 0.8) {
            return Component.translatable("gui.krios_industry.koios.phrase.almost");
        }
        long slot = (long) (time / 900) % 3;
        return Component.translatable("gui.krios_industry.koios.phrase.learning" + (slot + 1));
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float w, float h, float z,
            float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        vertex(pose, vc, x, y, z, u0, v1, r, g, b, a);
        vertex(pose, vc, x + w, y, z, u1, v1, r, g, b, a);
        vertex(pose, vc, x + w, y + h, z, u1, v0, r, g, b, a);
        vertex(pose, vc, x, y + h, z, u0, v0, r, g, b, a);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z,
            float u, float v, float r, float g, float b, float a) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, 0, 1);
    }
}
