package io.lakio.krios.industry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.lakio.krios.industry.client.BlueprintState;
import io.lakio.krios.industry.tool.Blueprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * In-world blueprint hologram (art direction): missing blocks render as
 * translucent ghost models, wrong blocks get a red overlay, correctly
 * placed blocks simply stop ghosting — the blueprint "fills in" as you
 * build and clears itself on completion. Hooked after the translucent
 * stage by each loader's client entry.
 */
public final class BlueprintRenderer {

    public static void render(PoseStack poseStack, float partialTick) {
        BlockPos anchor = BlueprintState.anchor();
        Blueprint blueprint = BlueprintState.blueprint();
        Minecraft minecraft = Minecraft.getInstance();
        if (anchor == null || blueprint == null || minecraft.level == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean complete = true;
        for (Blueprint.Cell cell : blueprint.cells()) {
            BlockPos target = anchor.offset(cell.offset());
            BlockState want = cell.block().get().defaultBlockState();
            BlockState have = minecraft.level.getBlockState(target);
            if (have.is(want.getBlock())) {
                continue;
            }
            complete = false;
            poseStack.pushPose();
            poseStack.translate(target.getX() - camera.x, target.getY() - camera.y,
                    target.getZ() - camera.z);
            if (have.isAir() || have.canBeReplaced()) {
                MultiBufferSource ghost = type -> new GhostVertexConsumer(
                        buffers.getBuffer(RenderType.translucentMovingBlock()), 90);
                minecraft.getBlockRenderer().renderSingleBlock(want, poseStack, ghost,
                        0xF000F0, OverlayTexture.NO_OVERLAY);
            } else {
                // Wrong block in the slot: red translucent box, slightly
                // inflated so it reads over the block's own faces.
                drawBox(poseStack, buffers.getBuffer(RenderType.debugQuads()),
                        -0.01F, 1.01F, 0.9F, 0.2F, 0.2F, 0.45F);
            }
            poseStack.popPose();
        }
        buffers.endBatch();
        if (complete) {
            BlueprintState.clear();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "gui.krios_industry.calibrator.blueprint_complete"), true);
            }
        }
    }

    /** Axis-aligned box [min,max]^3 in local space, all six faces. */
    private static void drawBox(PoseStack poseStack, VertexConsumer vc, float min, float max,
            float r, float g, float b, float a) {
        var m = poseStack.last().pose();
        // Two opposite faces per axis, outward winding.
        vc.addVertex(m, min, min, min).setColor(r, g, b, a);
        vc.addVertex(m, min, max, min).setColor(r, g, b, a);
        vc.addVertex(m, max, max, min).setColor(r, g, b, a);
        vc.addVertex(m, max, min, min).setColor(r, g, b, a);

        vc.addVertex(m, max, min, max).setColor(r, g, b, a);
        vc.addVertex(m, max, max, max).setColor(r, g, b, a);
        vc.addVertex(m, min, max, max).setColor(r, g, b, a);
        vc.addVertex(m, min, min, max).setColor(r, g, b, a);

        vc.addVertex(m, min, min, max).setColor(r, g, b, a);
        vc.addVertex(m, min, max, max).setColor(r, g, b, a);
        vc.addVertex(m, min, max, min).setColor(r, g, b, a);
        vc.addVertex(m, min, min, min).setColor(r, g, b, a);

        vc.addVertex(m, max, min, min).setColor(r, g, b, a);
        vc.addVertex(m, max, max, min).setColor(r, g, b, a);
        vc.addVertex(m, max, max, max).setColor(r, g, b, a);
        vc.addVertex(m, max, min, max).setColor(r, g, b, a);

        vc.addVertex(m, min, max, min).setColor(r, g, b, a);
        vc.addVertex(m, min, max, max).setColor(r, g, b, a);
        vc.addVertex(m, max, max, max).setColor(r, g, b, a);
        vc.addVertex(m, max, max, min).setColor(r, g, b, a);

        vc.addVertex(m, min, min, max).setColor(r, g, b, a);
        vc.addVertex(m, min, min, min).setColor(r, g, b, a);
        vc.addVertex(m, max, min, min).setColor(r, g, b, a);
        vc.addVertex(m, max, min, max).setColor(r, g, b, a);
    }

    /** Delegating vertex consumer that caps the alpha channel (ghost look). */
    private record GhostVertexConsumer(VertexConsumer delegate, int alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(r, g, b, Math.min(a, alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    private BlueprintRenderer() {
    }
}
