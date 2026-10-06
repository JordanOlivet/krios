package io.lakio.krios.industry.client.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.lakio.krios.industry.client.screen.HologramFacePanel;
import io.lakio.krios.industry.machine.ConfigurableFaces;
import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.tool.CalibratorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * In-world face-config overlay (read-only): while the player holds the
 * Calibrator, nearby machines draw their per-face flow badges directly on
 * the block — the same effective-state language as the GUI hologram
 * (manual full strength, red cross on a fully blocked manual face,
 * default/auto dim with routed brighter) plus a pulsing amber frame on
 * symmetric-pair hint faces, the "configure this pair" nudge from the
 * design doc. Purely visual; configuration stays in the machine
 * terminals. Face bits reach the client through the machines' BE update
 * tags. Hooked after the translucent stage with the blueprint renderer;
 * depth testing against the opaque world hides back and buried faces.
 */
public final class FaceOverlayRenderer {

    /** Machines beyond this many blocks stay silent (badge legibility). */
    private static final int RANGE = 8;
    /** Badges float just off the face; depth precision is fine this close. */
    private static final float SURFACE = 0.51F;
    private static final int HINT_RGB = 0xFFB03A;
    private static final float BADGE_SPREAD = 0.16F;

    /** Two masks today (reactor vs grinder/forge); derived once each. */
    private static final Map<Short, HologramFacePanel.Column[]> COLUMN_CACHE = new HashMap<>();

    public static void render(PoseStack poseStack, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !holdsCalibrator(player)) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 eye = player.getEyePosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        float pulse = 0.30F + 0.25F * Mth.sin((minecraft.level.getGameTime() + partialTick) * 0.25F);
        BlockPos center = player.blockPosition();
        int minChunkX = (center.getX() - RANGE) >> 4;
        int maxChunkX = (center.getX() + RANGE) >> 4;
        int minChunkZ = (center.getZ() - RANGE) >> 4;
        int maxChunkZ = (center.getZ() + RANGE) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!(minecraft.level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)
                        instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ConfigurableFaces machine)) {
                        continue;
                    }
                    BlockPos pos = blockEntity.getBlockPos();
                    double dist = Math.sqrt(pos.distToCenterSqr(eye.x, eye.y, eye.z));
                    if (dist > RANGE) {
                        continue;
                    }
                    // Soft fade over the last two blocks of range.
                    float fade = Mth.clamp((RANGE - (float) dist) / 2F, 0F, 1F);
                    poseStack.pushPose();
                    poseStack.translate(pos.getX() + 0.5 - camera.x,
                            pos.getY() + 0.5 - camera.y, pos.getZ() + 0.5 - camera.z);
                    renderMachine(poseStack, vc, machine, fade, pulse);
                    poseStack.popPose();
                }
            }
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    private static boolean holdsCalibrator(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof CalibratorItem
                || player.getOffhandItem().getItem() instanceof CalibratorItem;
    }

    private static void renderMachine(PoseStack pose, VertexConsumer vc,
            ConfigurableFaces machine, float fade, float pulse) {
        HologramFacePanel.Column[] columns = COLUMN_CACHE.computeIfAbsent(
                machine.supportedFaceBits(), HologramFacePanel::columnsFor);
        for (Direction dir : Direction.values()) {
            int bits = machine.faceConfig().bits(dir);
            Vector3f n = new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            Vector3f u = dir.getAxis() == Direction.Axis.Y
                    ? new Vector3f(1, 0, 0)
                    : new Vector3f(dir.getCounterClockWise().getStepX(), 0,
                            dir.getCounterClockWise().getStepZ());
            Vector3f v = new Vector3f(n).cross(u);
            // Same badge basis as the GUI hologram, minus its y-flip games:
            // debugQuads culls nothing, depth testing does the hiding.
            if (new Vector3f(u).cross(v).dot(n) < 0) {
                v.negate();
            }
            int shown = 0;
            for (HologramFacePanel.Column column : columns) {
                if (HologramFacePanel.badgeAlpha(bits, column.flow(), column.out()) >= 0) {
                    shown++;
                }
            }
            if (shown == 0 && FaceConfig.isManualBits(bits)) {
                cross(pose, vc, n, u, v, fade);
            } else {
                float start = -BADGE_SPREAD * (shown - 1) / 2F;
                int index = 0;
                for (HologramFacePanel.Column column : columns) {
                    int alpha = HologramFacePanel.badgeAlpha(bits, column.flow(), column.out());
                    if (alpha < 0) {
                        continue;
                    }
                    arrow(pose, vc, n, u, v, start + index++ * BADGE_SPREAD, column.out(),
                            HologramFacePanel.flowColor(column.flow()), alpha / 255F * fade);
                }
            }
            if (FaceConfig.hasHintBits(bits)) {
                hintFrame(pose, vc, n, u, v, pulse * fade);
            }
        }
    }

    /** Shaft then head, small quads in the face plane (GUI badge geometry). */
    private static void arrow(PoseStack pose, VertexConsumer vc, Vector3f n, Vector3f u,
            Vector3f v, float uOffset, boolean out, int rgb, float alpha) {
        float sign = out ? 1F : -1F;
        quad(pose, vc, n, u, v, uOffset - 0.021F, -0.10F * sign, uOffset + 0.021F, 0.035F * sign, rgb, alpha);
        quad(pose, vc, n, u, v, uOffset - 0.07F, 0.028F * sign, uOffset + 0.07F, 0.063F * sign, rgb, alpha);
        quad(pose, vc, n, u, v, uOffset - 0.035F, 0.063F * sign, uOffset + 0.035F, 0.112F * sign, rgb, alpha);
    }

    private static void cross(PoseStack pose, VertexConsumer vc, Vector3f n, Vector3f u,
            Vector3f v, float fade) {
        quad(pose, vc, n, u, v, -0.16F, -0.04F, 0.16F, 0.04F, 0xE5484D, 0.82F * fade);
        quad(pose, vc, n, u, v, -0.04F, -0.16F, 0.04F, 0.16F, 0xE5484D, 0.82F * fade);
    }

    /** Pulsing border on a symmetric-pair hint face; segments never overlap. */
    private static void hintFrame(PoseStack pose, VertexConsumer vc, Vector3f n, Vector3f u,
            Vector3f v, float alpha) {
        float outer = 0.48F;
        float inner = 0.44F;
        quad(pose, vc, n, u, v, -outer, inner, outer, outer, HINT_RGB, alpha);
        quad(pose, vc, n, u, v, -outer, -outer, outer, -inner, HINT_RGB, alpha);
        quad(pose, vc, n, u, v, -outer, -inner, -inner, inner, HINT_RGB, alpha);
        quad(pose, vc, n, u, v, inner, -inner, outer, inner, HINT_RGB, alpha);
    }

    /** Axis-aligned quad in the face plane, corners (u0,v0)-(u1,v1). */
    private static void quad(PoseStack pose, VertexConsumer vc, Vector3f n, Vector3f u,
            Vector3f v, float u0, float v0, float u1, float v1, int rgb, float alpha) {
        if (v1 < v0) {
            float t = v0;
            v0 = v1;
            v1 = t;
        }
        float r = ((rgb >> 16) & 0xFF) / 255F;
        float g = ((rgb >> 8) & 0xFF) / 255F;
        float b = (rgb & 0xFF) / 255F;
        var matrix = pose.last().pose();
        float[][] corners = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (float[] p : corners) {
            vc.addVertex(matrix,
                    n.x * SURFACE + u.x * p[0] + v.x * p[1],
                    n.y * SURFACE + u.y * p[0] + v.y * p[1],
                    n.z * SURFACE + u.z * p[0] + v.z * p[1]).setColor(r, g, b, alpha);
        }
    }

    private FaceOverlayRenderer() {
    }
}
