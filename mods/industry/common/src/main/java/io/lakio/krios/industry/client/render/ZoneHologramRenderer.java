package io.lakio.krios.industry.client.render;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.lakio.krios.industry.client.ZoneHologramState;
import io.lakio.krios.industry.machine.ZoneGeometry;
import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.tool.CalibratorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Zone hologram (Chrono Farmer design 2026-10-08): a cyan wireframe box
 * over the zone's crop layer with a faint floor. Shown for every zone
 * machine in range while the Calibrator is held, and for the machines the
 * player pinned from their terminal. The zone edge reaches the client
 * through the machine's BE update tag; the shape comes from the same
 * {@link ZoneGeometry} the server scans. Hooked after the translucent
 * stage with the other overlays.
 */
public final class ZoneHologramRenderer {

    /** Calibrator reach: a 27x27 zone must stay visible from its far side. */
    private static final int RANGE = 48;
    private static final float R = 0x31 / 255F;
    private static final float G = 0xD8 / 255F;
    private static final float B = 0xFF / 255F;
    private static final float LINE = 0.08F;

    public static void render(PoseStack poseStack, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        Set<BlockPos> targets = new HashSet<>();
        Iterator<BlockPos> pinned = ZoneHologramState.pinned().iterator();
        while (pinned.hasNext()) {
            BlockPos pos = pinned.next();
            if (!minecraft.level.isLoaded(pos)) {
                continue;
            }
            if (minecraft.level.getBlockEntity(pos) instanceof ZoneMachineBlockEntity) {
                targets.add(pos);
            } else {
                // The machine is gone: the pin goes with it.
                pinned.remove();
            }
        }
        if (holdsCalibrator(player)) {
            collectInRange(minecraft, player.blockPosition(), targets);
        }
        if (targets.isEmpty()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        float pulse = 0.5F + 0.15F * Mth.sin((minecraft.level.getGameTime() + partialTick) * 0.15F);
        for (BlockPos pos : targets) {
            if (!(minecraft.level.getBlockEntity(pos) instanceof ZoneMachineBlockEntity machine)) {
                continue;
            }
            int edge = machine.zoneEdgeForDisplay();
            if (edge <= 0) {
                continue;
            }
            BlockPos min = ZoneGeometry.min(pos, machine.zoneDirection(), edge);
            BlockPos max = ZoneGeometry.max(pos, machine.zoneDirection(), edge);
            poseStack.pushPose();
            poseStack.translate(min.getX() - camera.x, min.getY() - camera.y, min.getZ() - camera.z);
            drawZone(poseStack.last().pose(), vc,
                    max.getX() - min.getX() + 1, max.getZ() - min.getZ() + 1, pulse);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    private static void collectInRange(Minecraft minecraft, BlockPos center, Set<BlockPos> out) {
        for (int chunkX = (center.getX() - RANGE) >> 4; chunkX <= (center.getX() + RANGE) >> 4; chunkX++) {
            for (int chunkZ = (center.getZ() - RANGE) >> 4; chunkZ <= (center.getZ() + RANGE) >> 4; chunkZ++) {
                if (!(minecraft.level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)
                        instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof ZoneMachineBlockEntity
                            && blockEntity.getBlockPos().closerThan(center, RANGE)) {
                        out.add(blockEntity.getBlockPos());
                    }
                }
            }
        }
    }

    /** Local space: the zone spans [0,w] x [0,1] x [0,d] (crop layer). */
    private static void drawZone(Matrix4f m, VertexConsumer vc, float w, float d, float alpha) {
        float floor = 0.02F;
        float top = 1.0F;
        // Faint floor so the footprint reads even between crops.
        flat(m, vc, 0, floor, 0, w, d, alpha * 0.18F);
        for (float y : new float[] {floor, top}) {
            flat(m, vc, 0, y, 0, w, LINE, alpha);
            flat(m, vc, 0, y, d - LINE, w, d, alpha);
            flat(m, vc, 0, y, LINE, LINE, d - LINE, alpha);
            flat(m, vc, w - LINE, y, LINE, w, d - LINE, alpha);
        }
        // Corner posts, kept INSIDE the zone footprint (a post centered on
        // the corner spilled half its width onto the neighbor blocks).
        for (float x : new float[] {0, w - LINE}) {
            for (float z : new float[] {0, d - LINE}) {
                post(m, vc, x, z, floor, top, alpha);
            }
        }
    }

    /** Horizontal rect [x0,x1] x [z0,z1] at height y, both windings. */
    private static void flat(Matrix4f m, VertexConsumer vc, float x0, float y, float z0,
            float x1, float z1, float a) {
        vc.addVertex(m, x0, y, z0).setColor(R, G, B, a);
        vc.addVertex(m, x0, y, z1).setColor(R, G, B, a);
        vc.addVertex(m, x1, y, z1).setColor(R, G, B, a);
        vc.addVertex(m, x1, y, z0).setColor(R, G, B, a);
        vc.addVertex(m, x1, y, z0).setColor(R, G, B, a);
        vc.addVertex(m, x1, y, z1).setColor(R, G, B, a);
        vc.addVertex(m, x0, y, z1).setColor(R, G, B, a);
        vc.addVertex(m, x0, y, z0).setColor(R, G, B, a);
    }

    /** Thin square column [x, x+LINE] x [z, z+LINE], four sides, both windings. */
    private static void post(Matrix4f m, VertexConsumer vc, float x, float z, float y0, float y1, float a) {
        float x1 = x + LINE;
        float z1 = z + LINE;
        float[][] planes = {{x, z, x1, z}, {x, z1, x1, z1}, {x, z, x, z1}, {x1, z, x1, z1}};
        for (float[] p : planes) {
            vc.addVertex(m, p[0], y0, p[1]).setColor(R, G, B, a);
            vc.addVertex(m, p[0], y1, p[1]).setColor(R, G, B, a);
            vc.addVertex(m, p[2], y1, p[3]).setColor(R, G, B, a);
            vc.addVertex(m, p[2], y0, p[3]).setColor(R, G, B, a);
            vc.addVertex(m, p[2], y0, p[3]).setColor(R, G, B, a);
            vc.addVertex(m, p[2], y1, p[3]).setColor(R, G, B, a);
            vc.addVertex(m, p[0], y1, p[1]).setColor(R, G, B, a);
            vc.addVertex(m, p[0], y0, p[1]).setColor(R, G, B, a);
        }
    }

    private static boolean holdsCalibrator(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof CalibratorItem
                || player.getOffhandItem().getItem() instanceof CalibratorItem;
    }

    private ZoneHologramRenderer() {
    }
}
