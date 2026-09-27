package io.lakio.krios.industry.client.screen;

import java.util.function.IntUnaryOperator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.Flow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Holographic face-config view (art direction): the machine rendered in 3D
 * with its real neighbors as translucent ghosts. Left-click a face to
 * select it, right-drag to rotate freely; the selected face's contract is
 * edited through the same vanilla menu-button channel as before
 * (face*16 + sub, 15 = mode toggle) — no custom packets.
 */
public class HologramFacePanel {

    /** A supported flow direction; sub id encodes it as flow*2 + out. */
    public record Column(Flow flow, boolean out) {
        int sub() {
            return flow.ordinal() * 2 + (out ? 1 : 0);
        }
    }

    /** Internal click result: consumed by the panel, no menu button to send. */
    public static final int CLICK_CONSUMED = -2;

    private static final String[] FACE_KEYS = {
            "gui.krios_industry.direction.down", "gui.krios_industry.direction.up",
            "gui.krios_industry.direction.north", "gui.krios_industry.direction.south",
            "gui.krios_industry.direction.west", "gui.krios_industry.direction.east"};

    private static final int PANEL_W = 140;
    private static final int VP_X = 6;
    private static final int VP_Y = 16;
    private static final int VP_W = 128;
    private static final int VP_H = 100;
    private static final float SCALE = 24;
    private static final int MODE_ROW_Y = VP_Y + VP_H + 4;
    private static final int ROW_H = 13;
    private static final int ROW_STEP = 15;
    private static final int PILL_W = 40;

    /** Cube face corners in [-0.5, 0.5] model space, seen from outside. */
    private static final float[][][] FACE_CORNERS = new float[6][][];

    static {
        for (Direction dir : Direction.values()) {
            float[][] corners = new float[4][];
            Direction.Axis axis = dir.getAxis();
            float sign = dir.getAxisDirection().getStep() * 0.5F;
            int i = 0;
            for (int a = -1; a <= 1; a += 2) {
                for (int b = -1; b <= 1; b += 2) {
                    corners[i++] = switch (axis) {
                        case X -> new float[]{sign, a * 0.5F, b * 0.5F};
                        case Y -> new float[]{a * 0.5F, sign, b * 0.5F};
                        case Z -> new float[]{a * 0.5F, b * 0.5F, sign};
                    };
                }
            }
            FACE_CORNERS[dir.get3DDataValue()] = corners;
        }
    }

    private final Column[] columns;
    /** Column order grouped by flow type, one UI row per flow. */
    private final java.util.List<Flow> flowRows;
    private boolean open;
    private Direction selected;
    private Direction hovered;
    private float yaw = Float.NaN;
    private float pitch = 0.5F;
    private Matrix4f lastTransform = new Matrix4f();

    public HologramFacePanel(Column... columns) {
        this.columns = columns;
        this.flowRows = new java.util.ArrayList<>();
        for (Column column : columns) {
            if (!flowRows.contains(column.flow())) {
                flowRows.add(column.flow());
            }
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void toggleOpen() {
        open = !open;
    }

    public int width() {
        return PANEL_W;
    }

    public int height() {
        return MODE_ROW_Y + ROW_STEP + flowRows.size() * ROW_STEP + 6;
    }

    public void render(GuiGraphics gui, Font font, int x, int y, IntUnaryOperator bits, BlockPos machinePos) {
        if (!open) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        gui.fill(x, y, x + PANEL_W, y + height(), 0xE8101418);
        gui.drawString(font, Component.translatable("gui.krios_industry.faces.title"),
                x + 6, y + 4, 0xFF31D8FF, false);

        // Viewport frame.
        int vx = x + VP_X;
        int vy = y + VP_Y;
        gui.fill(vx - 1, vy - 1, vx + VP_W + 1, vy + VP_H + 1, 0xFF0A6E87);
        gui.fill(vx, vy, vx + VP_W, vy + VP_H, 0xFF090C12);

        BlockState state = minecraft.level != null ? minecraft.level.getBlockState(machinePos) : null;
        if (state == null || state.isAir()) {
            return;
        }
        if (Float.isNaN(yaw)) {
            yaw = initialYaw(state);
        }

        float cx = vx + VP_W / 2F;
        float cy = vy + VP_H / 2F;
        lastTransform = new Matrix4f()
                .translate(cx, cy, 150)
                .scale(SCALE, -SCALE, SCALE)
                .rotateX(pitch)
                .rotateY(yaw);

        gui.enableScissor(vx, vy, vx + VP_W, vy + VP_H);
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(cx, cy, 150);
        pose.scale(SCALE, -SCALE, SCALE);
        pose.mulPose(new Quaternionf().rotationX(pitch).rotateY(yaw));

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // Machine first, then the ghosts blend over it.
        pose.pushPose();
        pose.translate(-0.5F, -0.5F, -0.5F);
        minecraft.getBlockRenderer().renderSingleBlock(state, pose, buffers,
                0xF000F0, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        buffers.endBatch();
        for (Direction dir : Direction.values()) {
            BlockState neighbor = minecraft.level.getBlockState(machinePos.relative(dir));
            if (neighbor.isAir() || neighbor.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            pose.pushPose();
            pose.translate(dir.getStepX() - 0.5F, dir.getStepY() - 0.5F, dir.getStepZ() - 0.5F);
            MultiBufferSource ghost = type -> new GhostVertexConsumer(
                    buffers.getBuffer(RenderType.translucentMovingBlock()), 55);
            minecraft.getBlockRenderer().renderSingleBlock(neighbor, pose, ghost,
                    0xF000F0, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            buffers.endBatch();
        }

        // Face highlights: hovered (faint) and selected (strong). guiOverlay
        // skips the depth test so the selection reads through the ghosts.
        VertexConsumer quads = buffers.getBuffer(RenderType.guiOverlay());
        if (hovered != null && hovered != selected) {
            faceQuad(pose, quads, hovered, 0x4031D8FF);
        }
        if (selected != null) {
            faceQuad(pose, quads, selected, 0x9031D8FF);
        }
        buffers.endBatch();
        pose.popPose();
        gui.disableScissor();

        renderControls(gui, font, x, y, bits);
    }

    private void renderControls(GuiGraphics gui, Font font, int x, int y, IntUnaryOperator bits) {
        if (selected == null) {
            int textY = y + MODE_ROW_Y + 2;
            for (FormattedCharSequence line : font.split(
                    Component.translatable("gui.krios_industry.faces.hint"), PANEL_W - 12)) {
                gui.drawString(font, line, x + 6, textY, 0xFF5A6170, false);
                textY += 10;
            }
            return;
        }
        int faceBits = bits.applyAsInt(selected.get3DDataValue());
        boolean manual = FaceConfig.isManualBits(faceBits);

        gui.drawString(font, Component.translatable(FACE_KEYS[selected.get3DDataValue()]),
                x + 6, y + MODE_ROW_Y + 3, 0xFFE8EAF0, false);
        int modeX = x + PANEL_W - 60;
        gui.fill(modeX, y + MODE_ROW_Y, modeX + 54, y + MODE_ROW_Y + ROW_H,
                manual ? 0xFF0A6E87 : 0xFF262B35);
        gui.drawCenteredString(font, Component.translatable(manual
                        ? "gui.krios_industry.faces.manual"
                        : FaceConfig.isConfiguredBits(faceBits)
                                ? "gui.krios_industry.faces.auto"
                                : "gui.krios_industry.faces.vanilla"),
                modeX + 27, y + MODE_ROW_Y + 3, manual ? 0xFFE8EAF0 : 0xFF9FA6AA);

        // One row per flow type, a labelled Input/Output pill per direction
        // the machine supports. Filled cyan = allowed, dark = blocked; in
        // non-manual modes the pills are informational (auto routing).
        for (int row = 0; row < flowRows.size(); row++) {
            Flow flow = flowRows.get(row);
            int rowY = y + MODE_ROW_Y + ROW_STEP + row * ROW_STEP;
            gui.drawString(font, Component.translatable(
                    "gui.krios_industry.flowtype." + flow.name().toLowerCase()),
                    x + 6, rowY + 3, 0xFFA9AFBD, false);
            int pillX = x + PANEL_W - 6 - PILL_W;
            for (int i = columns.length - 1; i >= 0; i--) {
                Column column = columns[i];
                if (column.flow() != flow) {
                    continue;
                }
                boolean allowed = FaceConfig.allowsBits(faceBits, column.flow(), column.out());
                int bg;
                int fg;
                if (manual) {
                    bg = allowed ? 0xFF0A6E87 : 0xFF262B35;
                    fg = allowed ? 0xFFE8EAF0 : 0xFF5A6170;
                } else {
                    bg = 0xFF171B22;
                    fg = FaceConfig.isConfiguredBits(faceBits) && allowed ? 0xFF9AD9E0 : 0xFF5A6170;
                }
                gui.fill(pillX, rowY, pillX + PILL_W, rowY + ROW_H, bg);
                gui.drawCenteredString(font, Component.translatable(column.out()
                        ? "gui.krios_industry.faces.out" : "gui.krios_industry.faces.in"),
                        pillX + PILL_W / 2, rowY + 3, fg);
                pillX -= PILL_W + 4;
            }
        }
    }

    /**
     * Mouse handling. Returns a menu button id (>= 0) to send, CLICK_CONSUMED
     * when the panel handled the click itself, or -1 when the click is
     * outside the panel.
     */
    public int click(int x, int y, double mouseX, double mouseY, int button, IntUnaryOperator bits) {
        if (!open || mouseX < x || mouseX >= x + PANEL_W || mouseY < y || mouseY >= y + height()) {
            return -1;
        }
        // Viewport: left picks a face, right starts a drag (handled in mouseDragged).
        if (inViewport(x, y, mouseX, mouseY)) {
            if (button == 0) {
                selected = pickFace(mouseX, mouseY);
            }
            return CLICK_CONSUMED;
        }
        if (selected == null || button != 0) {
            return CLICK_CONSUMED;
        }
        int face = selected.get3DDataValue();
        // Mode toggle.
        int modeX = x + PANEL_W - 60;
        if (mouseX >= modeX && mouseX < modeX + 54
                && mouseY >= y + MODE_ROW_Y && mouseY < y + MODE_ROW_Y + ROW_H) {
            return face * 16 + 15;
        }
        // Flow pills (manual mode only), same layout as renderControls.
        if (FaceConfig.isManualBits(bits.applyAsInt(face))) {
            for (int row = 0; row < flowRows.size(); row++) {
                Flow flow = flowRows.get(row);
                int rowY = y + MODE_ROW_Y + ROW_STEP + row * ROW_STEP;
                if (mouseY < rowY || mouseY >= rowY + ROW_H) {
                    continue;
                }
                int pillX = x + PANEL_W - 6 - PILL_W;
                for (int i = columns.length - 1; i >= 0; i--) {
                    Column column = columns[i];
                    if (column.flow() != flow) {
                        continue;
                    }
                    if (mouseX >= pillX && mouseX < pillX + PILL_W) {
                        return face * 16 + column.sub();
                    }
                    pillX -= PILL_W + 4;
                }
            }
        }
        return CLICK_CONSUMED;
    }

    /** Right-drag inside the viewport rotates the hologram. */
    public boolean mouseDragged(int x, int y, double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        if (!open || button != 1 || !inViewport(x, y, mouseX, mouseY)) {
            return false;
        }
        yaw += (float) dragX * 0.03F;
        pitch = Mth.clamp(pitch + (float) dragY * 0.03F, -1.4F, 1.4F);
        return true;
    }

    /** Hover tracking; call every frame before render. */
    public void updateHover(int x, int y, double mouseX, double mouseY) {
        hovered = open && inViewport(x, y, mouseX, mouseY) ? pickFace(mouseX, mouseY) : null;
    }

    private boolean inViewport(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x + VP_X && mouseX < x + VP_X + VP_W
                && mouseY >= y + VP_Y && mouseY < y + VP_Y + VP_H;
    }

    /** Front toward the viewer when the block has a horizontal facing. */
    private float initialYaw(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return (float) Math.atan2(-facing.getStepX(), facing.getStepZ());
        }
        return (float) Math.PI * 0.75F;
    }

    /**
     * Projects each cube face through the render transform and returns the
     * closest front face containing the cursor.
     */
    private Direction pickFace(double mouseX, double mouseY) {
        Direction best = null;
        float bestDepth = Float.NEGATIVE_INFINITY;
        for (Direction dir : Direction.values()) {
            float[][] corners = FACE_CORNERS[dir.get3DDataValue()];
            float[] px = new float[4];
            float[] py = new float[4];
            float depth = 0;
            for (int i = 0; i < 4; i++) {
                Vector4f v = lastTransform.transform(
                        new Vector4f(corners[i][0], corners[i][1], corners[i][2], 1));
                px[i] = v.x;
                py[i] = v.y;
                depth += v.z / 4;
            }
            if (containsPoint(px, py, (float) mouseX, (float) mouseY) && depth > bestDepth) {
                bestDepth = depth;
                best = dir;
            }
        }
        return best;
    }

    /** Point-in-quad; corners arrive as (00, 01, 11, 10) so order them. */
    private static boolean containsPoint(float[] px, float[] py, float mx, float my) {
        int[] order = {0, 1, 3, 2};
        boolean positive = false;
        boolean negative = false;
        for (int i = 0; i < 4; i++) {
            int a = order[i];
            int b = order[(i + 1) % 4];
            float cross = (px[b] - px[a]) * (my - py[a]) - (py[b] - py[a]) * (mx - px[a]);
            positive |= cross > 0;
            negative |= cross < 0;
        }
        return !(positive && negative);
    }

    /** Translucent highlight quad on a cube face, slightly offset outward. */
    private static void faceQuad(PoseStack pose, VertexConsumer vc, Direction dir, int argb) {
        float[][] corners = FACE_CORNERS[dir.get3DDataValue()];
        float ox = dir.getStepX() * 0.01F;
        float oy = dir.getStepY() * 0.01F;
        float oz = dir.getStepZ() * 0.01F;
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        var matrix = pose.last().pose();
        int[] order = {0, 1, 3, 2};
        // Both windings so the quad shows regardless of cull direction.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                int idx = order[pass == 0 ? i : 3 - i];
                vc.addVertex(matrix, corners[idx][0] + ox, corners[idx][1] + oy, corners[idx][2] + oz)
                        .setColor(r, g, b, a);
            }
        }
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
}
