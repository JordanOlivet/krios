package io.lakio.krios.industry.client.screen;

import java.util.ArrayList;
import java.util.List;
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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Holographic face-config view (art direction): the machine rendered in 3D
 * with its real neighbors as translucent ghosts, per-face flow badges drawn
 * on the cube itself, and a plain-words control grid — fixed Input/Output
 * columns, one row per flow type, three-state slide switches (left+red =
 * off, right+cyan = on, centered+grey = auto) plus one-click presets.
 * Everything edits through the vanilla menu-button channel
 * (face*16 + sub; 15 = mode, 9..12 = presets) — no custom packets.
 * Docked RIGHT on the {@link AnnexPanel} chassis; coordinates below are
 * content-relative.
 */
public class HologramFacePanel extends AnnexPanel {

    /** A supported flow direction; sub id encodes it as flow*2 + out. */
    public record Column(Flow flow, boolean out) {
        int sub() {
            return flow.ordinal() * 2 + (out ? 1 : 0);
        }
    }

    /** Internal click result: consumed by the panel, no menu button to send. */
    public static final int CLICK_CONSUMED = -2;

    /** Preset sub ids (server side applies them atomically). */
    public static final int SUB_PRESET_BLOCK = 9;
    public static final int SUB_PRESET_IN = 10;
    public static final int SUB_PRESET_OUT = 11;
    public static final int SUB_PRESET_ALL = 12;

    private static final String[] FACE_KEYS = {
            "gui.krios_industry.direction.down", "gui.krios_industry.direction.up",
            "gui.krios_industry.direction.north", "gui.krios_industry.direction.south",
            "gui.krios_industry.direction.west", "gui.krios_industry.direction.east"};

    private static final int CONTENT_W = 132;
    private static final int VP_X = 2;
    private static final int VP_Y = 2;
    private static final int VP_W = 128;
    private static final int VP_H = 100;
    private static final float SCALE = 24;
    private static final int MODE_ROW_Y = VP_Y + VP_H + 4;
    private static final int PRESET_ROW_Y = MODE_ROW_Y + 15;
    private static final int HEADER_Y = PRESET_ROW_Y + 17;
    private static final int GRID_Y = HEADER_Y + 11;
    private static final int ROW_H = 13;
    private static final int ROW_STEP = 15;
    private static final int PRESET_SIZE = 14;
    private static final int SWITCH_W = 22;
    private static final int SWITCH_H = 10;
    private static final int KNOB = 8;
    /** Column centers for the Input / Output grid columns. */
    private static final int COL_IN_X = 52;
    private static final int COL_OUT_X = 94;
    private static final int COL_W = 40;

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
    /** Column order grouped by flow type, one grid row per flow. */
    private final List<Flow> flowRows;
    private Direction selected;
    private Direction hovered;
    private float yaw = Float.NaN;
    private float pitch = 0.5F;
    private Matrix4f lastTransform = new Matrix4f();
    /** Animated knob position per column, 0 = left, 0.5 = center, 1 = right. */
    private final float[] knobPos;
    private Direction knobFace;

    public HologramFacePanel(Column... columns) {
        super(Side.RIGHT, 4, "gui.krios_industry.faces.title",
                io.lakio.krios.industry.client.ClientPrefs.facesOpen());
        this.columns = columns;
        this.flowRows = new ArrayList<>();
        for (Column column : columns) {
            if (!flowRows.contains(column.flow())) {
                flowRows.add(column.flow());
            }
        }
        this.knobPos = new float[columns.length];
    }

    @Override
    protected void onToggle(boolean open) {
        io.lakio.krios.industry.client.ClientPrefs.setFacesOpen(open);
        if (open) {
            // Re-align the view with the player's current look on each open.
            yaw = Float.NaN;
        }
    }

    @Override
    protected int contentWidth() {
        return CONTENT_W;
    }

    @Override
    protected int contentHeight() {
        return GRID_Y + flowRows.size() * ROW_STEP + 2;
    }

    @Override
    protected void drawTabIcon(GuiGraphics gui, int x, int y) {
        // A cube with a face pip: the hologram at a glance.
        gui.fill(x, y, x + 8, y + 1, 0xFF31D8FF);
        gui.fill(x, y + 7, x + 8, y + 8, 0xFF31D8FF);
        gui.fill(x, y + 1, x + 1, y + 7, 0xFF31D8FF);
        gui.fill(x + 7, y + 1, x + 8, y + 7, 0xFF31D8FF);
        gui.fill(x + 3, y + 3, x + 5, y + 5, 0xFFFFB03A);
    }

    /** The BlockPos + face-bits payload arrives through the screen's render. */
    private IntUnaryOperator renderBits;
    private BlockPos renderPos;

    /** Stashes the live payload, then lets the chassis drive renderContent. */
    public void renderPanel(GuiGraphics gui, Font font, int leftPos, int topPos, int imageWidth,
            IntUnaryOperator bits, BlockPos machinePos, int mouseX, int mouseY) {
        this.renderBits = bits;
        this.renderPos = machinePos;
        render(gui, font, leftPos, topPos, imageWidth, mouseX, mouseY);
    }

    @Override
    protected void renderContent(GuiGraphics gui, Font font, int x, int y, int mouseX, int mouseY) {
        IntUnaryOperator bits = renderBits;
        BlockPos machinePos = renderPos;
        if (bits == null || machinePos == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();

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
            yaw = initialYaw(minecraft);
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

        // Face highlights and per-face flow badges. guiOverlay skips the
        // depth test so both read through the ghosts.
        VertexConsumer quads = buffers.getBuffer(RenderType.guiOverlay());
        if (hovered != null && hovered != selected) {
            faceQuad(pose, quads, hovered, 0x4031D8FF);
        }
        if (selected != null) {
            faceQuad(pose, quads, selected, 0x9031D8FF);
        }
        for (Direction dir : Direction.values()) {
            renderBadges(pose, quads, dir, bits.applyAsInt(dir.get3DDataValue()));
        }
        buffers.endBatch();
        pose.popPose();
        gui.disableScissor();

        renderControls(gui, font, x, y, bits, mouseX, mouseY);
    }

    // ------------------------------------------------------------ controls

    private void renderControls(GuiGraphics gui, Font font, int x, int y, IntUnaryOperator bits,
            int mouseX, int mouseY) {
        if (selected == null) {
            int textY = y + MODE_ROW_Y + 2;
            for (FormattedCharSequence line : font.split(
                    Component.translatable("gui.krios_industry.faces.hint"), CONTENT_W - 4)) {
                gui.drawString(font, line, x + 2, textY, 0xFF5A6170, false);
                textY += 10;
            }
            return;
        }
        int faceBits = bits.applyAsInt(selected.get3DDataValue());
        boolean manual = FaceConfig.isManualBits(faceBits);

        // Face name + mode toggle.
        gui.drawString(font, Component.translatable(FACE_KEYS[selected.get3DDataValue()]),
                x + 2, y + MODE_ROW_Y + 3, 0xFFE8EAF0, false);
        int modeX = x + CONTENT_W - 56;
        gui.fill(modeX, y + MODE_ROW_Y, modeX + 54, y + MODE_ROW_Y + ROW_H,
                manual ? 0xFF0A6E87 : 0xFF262B35);
        gui.drawCenteredString(font, Component.translatable(manual
                        ? "gui.krios_industry.faces.manual"
                        : FaceConfig.isConfiguredBits(faceBits)
                                ? "gui.krios_industry.faces.auto"
                                : "gui.krios_industry.faces.vanilla"),
                modeX + 27, y + MODE_ROW_Y + 3, manual ? 0xFFE8EAF0 : 0xFF9FA6AA);

        // Presets: block / input only / output only / allow all.
        Component presetTip = null;
        for (int i = 0; i < 4; i++) {
            int px = x + 2 + i * (PRESET_SIZE + 4);
            int py = y + PRESET_ROW_Y;
            boolean hover = mouseX >= px && mouseX < px + PRESET_SIZE
                    && mouseY >= py && mouseY < py + PRESET_SIZE;
            gui.fill(px, py, px + PRESET_SIZE, py + PRESET_SIZE, hover ? 0xFF3A414E : 0xFF262B35);
            drawPresetIcon(gui, i, px, py);
            if (hover) {
                presetTip = Component.translatable(switch (i) {
                    case 0 -> "gui.krios_industry.faces.preset.block";
                    case 1 -> "gui.krios_industry.faces.preset.in";
                    case 2 -> "gui.krios_industry.faces.preset.out";
                    default -> "gui.krios_industry.faces.preset.all";
                });
            }
        }

        // Grid headers: fixed Input / Output columns.
        gui.drawCenteredString(font, Component.translatable("gui.krios_industry.faces.in"),
                x + COL_IN_X + COL_W / 2, y + HEADER_Y, 0xFF5A6170);
        gui.drawCenteredString(font, Component.translatable("gui.krios_industry.faces.out"),
                x + COL_OUT_X + COL_W / 2, y + HEADER_Y, 0xFF5A6170);

        // One row per flow type; a three-state switch per supported direction.
        if (selected != knobFace) {
            knobFace = selected;
            for (int i = 0; i < columns.length; i++) {
                knobPos[i] = knobTarget(faceBits, columns[i], manual);
            }
        }
        for (int row = 0; row < flowRows.size(); row++) {
            Flow flow = flowRows.get(row);
            int rowY = y + GRID_Y + row * ROW_STEP;
            gui.drawString(font, Component.translatable(
                    "gui.krios_industry.flowtype." + flow.name().toLowerCase()),
                    x + 2, rowY + 2, 0xFFA9AFBD, false);
            for (boolean out : new boolean[]{false, true}) {
                int colX = x + (out ? COL_OUT_X : COL_IN_X);
                int index = columnIndex(flow, out);
                if (index < 0) {
                    gui.drawCenteredString(font, Component.literal("—"),
                            colX + COL_W / 2, rowY + 2, 0xFF31363F);
                    continue;
                }
                float target = knobTarget(faceBits, columns[index], manual);
                knobPos[index] += (target - knobPos[index]) * 0.35F;
                drawSwitch(gui, colX + (COL_W - SWITCH_W) / 2, rowY + 1, knobPos[index],
                        manual, FaceConfig.allowsBits(faceBits, flow, out),
                        FaceConfig.isConfiguredBits(faceBits));
            }
        }
        if (presetTip != null) {
            gui.renderTooltip(font, presetTip, mouseX, mouseY);
        }
    }

    private static float knobTarget(int faceBits, Column column, boolean manual) {
        if (!manual) {
            return 0.5F;
        }
        return FaceConfig.allowsBits(faceBits, column.flow(), column.out()) ? 1F : 0F;
    }

    private int columnIndex(Flow flow, boolean out) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].flow() == flow && columns[i].out() == out) {
                return i;
            }
        }
        return -1;
    }

    /** Slide switch: left+red = off, right+cyan = on, centered+grey = auto. */
    private static void drawSwitch(GuiGraphics gui, int x, int y, float pos,
            boolean manual, boolean allowed, boolean configured) {
        gui.fill(x, y, x + SWITCH_W, y + SWITCH_H, 0xFF171B22);
        gui.fill(x, y, x + SWITCH_W, y + 1, 0xFF0C0E13);
        int knobX = x + 1 + Math.round(pos * (SWITCH_W - 2 - KNOB));
        int color;
        if (manual) {
            color = allowed ? 0xFF31D8FF : 0xFFE5484D;
        } else {
            // Auto/default: informational — light cyan when a flow is routed.
            color = configured && allowed ? 0xFF9AD9E0 : 0xFF5A6170;
        }
        gui.fill(knobX, y + 1, knobX + KNOB, y + SWITCH_H - 1, color);
    }

    private static void drawPresetIcon(GuiGraphics gui, int preset, int x, int y) {
        int cx = x + PRESET_SIZE / 2;
        switch (preset) {
            case 0 -> { // Block: bar in a frame.
                gui.fill(x + 3, y + 3, x + PRESET_SIZE - 3, y + PRESET_SIZE - 3, 0xFF171B22);
                gui.fill(x + 4, y + 6, x + PRESET_SIZE - 4, y + 8, 0xFFE5484D);
            }
            case 1 -> arrowIcon(gui, cx, y, true, 0xFF31D8FF);
            case 2 -> arrowIcon(gui, cx, y, false, 0xFF31D8FF);
            default -> { // All: both arrows side by side.
                arrowIcon(gui, cx - 3, y, true, 0xFF31D8FF);
                arrowIcon(gui, cx + 3, y, false, 0xFF31D8FF);
            }
        }
    }

    private static void arrowIcon(GuiGraphics gui, int cx, int y, boolean down, int color) {
        gui.fill(cx - 1, y + 3, cx + 1, y + 11, color);
        for (int i = 0; i < 3; i++) {
            int w = 3 - i;
            int rowY = down ? y + 8 + i : y + 6 - i;
            gui.fill(cx - w, rowY, cx + w, rowY + 1, color);
        }
    }

    // ------------------------------------------------------------ badges

    /**
     * Per-face flow badges drawn on the hologram: an in-plane arrow per
     * allowed flow (amber = energy, cyan = items; toward the center =
     * input), dimmed in auto mode, a red cross on a fully blocked manual
     * face. Single winding so back faces cull away.
     */
    private void renderBadges(PoseStack pose, VertexConsumer vc, Direction dir, int faceBits) {
        boolean manual = FaceConfig.isManualBits(faceBits);
        boolean configured = FaceConfig.isConfiguredBits(faceBits);
        Vector3f n = new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        // Skip faces turned away from the viewer: guiOverlay has no depth
        // test, so a back-face badge would bleed through the machine.
        if (lastTransform.transformDirection(new Vector3f(n)).z <= 0) {
            return;
        }
        Vector3f u = dir.getAxis() == Direction.Axis.Y
                ? new Vector3f(1, 0, 0)
                : new Vector3f(dir.getCounterClockWise().getStepX(), 0, dir.getCounterClockWise().getStepZ());
        Vector3f v = new Vector3f(n).cross(u);
        // Right-handed basis (u x v = n) so the arrow winding faces outward.
        if (new Vector3f(u).cross(v).dot(n) < 0) {
            v.negate();
        }

        // The badges show the EFFECTIVE state, always: manual = allowed flows
        // full strength (red cross when fully blocked); default/auto = every
        // supported flow stays vanilla-open (dim), auto-routed ones brighter.
        List<Column> shown = new ArrayList<>();
        List<Integer> alphas = new ArrayList<>();
        for (Column column : columns) {
            boolean allowed = FaceConfig.allowsBits(faceBits, column.flow(), column.out());
            if (manual) {
                if (allowed) {
                    shown.add(column);
                    alphas.add(0xE0);
                }
            } else {
                shown.add(column);
                alphas.add(configured && allowed ? 0x98 : 0x50);
            }
        }
        if (manual && shown.isEmpty()) {
            crossBadge(pose, vc, n, u, v);
            return;
        }
        float spread = 0.16F;
        float start = -spread * (shown.size() - 1) / 2F;
        for (int i = 0; i < shown.size(); i++) {
            Column column = shown.get(i);
            int color = column.flow() == Flow.ENERGY ? 0xFFB03A : 0x31D8FF;
            arrowBadge(pose, vc, n, u, v, start + i * spread, column.out(),
                    (alphas.get(i) << 24) | color);
        }
    }

    private static void arrowBadge(PoseStack pose, VertexConsumer vc, Vector3f n, Vector3f u,
            Vector3f v, float uOffset, boolean out, int argb) {
        float dirSign = out ? 1F : -1F;
        // Shaft then head, drawn as small quads in the face plane.
        quad3(pose, vc, n, u, v, uOffset - 0.021F, -0.10F * dirSign, uOffset + 0.021F, 0.035F * dirSign, argb);
        quad3(pose, vc, n, u, v, uOffset - 0.07F, 0.028F * dirSign, uOffset + 0.07F, 0.063F * dirSign, argb);
        quad3(pose, vc, n, u, v, uOffset - 0.035F, 0.063F * dirSign, uOffset + 0.035F, 0.112F * dirSign, argb);
    }

    private void crossBadge(PoseStack pose, VertexConsumer vc, Vector3f n, Vector3f u, Vector3f v) {
        int argb = 0xD0E5484D;
        quad3(pose, vc, n, u, v, -0.16F, -0.04F, 0.16F, 0.04F, argb);
        quad3(pose, vc, n, u, v, -0.04F, -0.16F, 0.04F, 0.16F, argb);
    }

    /** Axis-aligned quad in a face plane, corners (u0,v0)-(u1,v1). */
    private static void quad3(PoseStack pose, VertexConsumer vc, Vector3f n, Vector3f u,
            Vector3f v, float u0, float v0, float u1, float v1, int argb) {
        if (v1 < v0) {
            float t = v0;
            v0 = v1;
            v1 = t;
        }
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        var matrix = pose.last().pose();
        float[][] uvs = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        // Both windings: the GUI's y-flip inverts handedness, so a single
        // winding gets culled; back faces are already skipped in view space.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] p = uvs[pass == 0 ? i : 3 - i];
                float x = n.x * 0.53F + u.x * p[0] + v.x * p[1];
                float y = n.y * 0.53F + u.y * p[0] + v.y * p[1];
                float z = n.z * 0.53F + u.z * p[0] + v.z * p[1];
                vc.addVertex(matrix, x, y, z).setColor(r, g, b, a);
            }
        }
    }

    // ------------------------------------------------------------ input

    /**
     * Mouse handling. Returns a menu button id (>= 0) to send, CLICK_CONSUMED
     * when the panel handled the click itself, or -1 when the click is
     * outside the panel.
     */
    public int click(int x, int y, double mouseX, double mouseY, int button, IntUnaryOperator bits) {
        if (!isOpen() || mouseX < x || mouseX >= x + CONTENT_W || mouseY < y || mouseY >= y + contentHeight()) {
            return -1;
        }
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
        int modeX = x + CONTENT_W - 56;
        if (mouseX >= modeX && mouseX < modeX + 54
                && mouseY >= y + MODE_ROW_Y && mouseY < y + MODE_ROW_Y + ROW_H) {
            return face * 16 + 15;
        }
        // Presets.
        if (mouseY >= y + PRESET_ROW_Y && mouseY < y + PRESET_ROW_Y + PRESET_SIZE) {
            for (int i = 0; i < 4; i++) {
                int px = x + 2 + i * (PRESET_SIZE + 4);
                if (mouseX >= px && mouseX < px + PRESET_SIZE) {
                    return face * 16 + SUB_PRESET_BLOCK + i;
                }
            }
        }
        // Switches (manual mode only), same layout as renderControls.
        if (FaceConfig.isManualBits(bits.applyAsInt(face))) {
            for (int row = 0; row < flowRows.size(); row++) {
                int rowY = y + GRID_Y + row * ROW_STEP;
                if (mouseY < rowY || mouseY >= rowY + ROW_H) {
                    continue;
                }
                for (boolean out : new boolean[]{false, true}) {
                    int index = columnIndex(flowRows.get(row), out);
                    int colX = x + (out ? COL_OUT_X : COL_IN_X);
                    if (index >= 0 && mouseX >= colX && mouseX < colX + COL_W) {
                        return face * 16 + columns[index].sub();
                    }
                }
            }
        }
        return CLICK_CONSUMED;
    }

    /** Right-drag inside the viewport rotates the hologram. */
    public boolean mouseDragged(int x, int y, double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        if (!isOpen() || button != 1 || !inViewport(x, y, mouseX, mouseY)) {
            return false;
        }
        yaw += (float) dragX * 0.03F;
        pitch = Mth.clamp(pitch + (float) dragY * 0.03F, -1.4F, 1.4F);
        return true;
    }

    /** Hover tracking; call every frame before render. */
    public void updateHover(int x, int y, double mouseX, double mouseY) {
        hovered = isOpen() && inViewport(x, y, mouseX, mouseY) ? pickFace(mouseX, mouseY) : null;
    }

    private boolean inViewport(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x + VP_X && mouseX < x + VP_X + VP_W
                && mouseY >= y + VP_Y && mouseY < y + VP_Y + VP_H;
    }

    /**
     * The hologram opens oriented like the player's own view of the block:
     * the face the player is looking at faces the viewer.
     */
    private float initialYaw(Minecraft minecraft) {
        if (minecraft.player == null) {
            return (float) Math.PI * 0.75F;
        }
        Direction toward = minecraft.player.getDirection().getOpposite();
        return (float) Math.atan2(-toward.getStepX(), toward.getStepZ());
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
