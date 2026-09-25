package io.lakio.krios.industry.client.screen;

import java.util.function.IntUnaryOperator;

import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.Flow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Placeholder face-config side panel shared by the machine screens (real
 * look lands with the graphics pass). One row per face: a mode cell
 * (vanilla/manual) then, in manual mode, one toggle cell per flow the
 * machine supports. Clicks return the button id the menu channel expects
 * (face*16 + sub, 15 = mode toggle) or -1.
 */
public class FaceConfigPanel {

    /** A flow-direction column: label key + encoded sub id (flow*2 + out). */
    public record Column(String labelKey, Flow flow, boolean out) {
        int sub() {
            return flow.ordinal() * 2 + (out ? 1 : 0);
        }
    }

    private static final String[] FACE_KEYS = {
            "gui.krios_industry.direction.down", "gui.krios_industry.direction.up",
            "gui.krios_industry.direction.north", "gui.krios_industry.direction.south",
            "gui.krios_industry.direction.west", "gui.krios_industry.direction.east"};

    private static final int ROW_HEIGHT = 13;
    private static final int HEADER_HEIGHT = 12;
    private static final int LABEL_WIDTH = 34;
    private static final int MODE_WIDTH = 26;
    private static final int CELL_WIDTH = 22;

    private final Column[] columns;
    private boolean open;

    public FaceConfigPanel(Column... columns) {
        this.columns = columns;
    }

    public boolean isOpen() {
        return open;
    }

    public void toggleOpen() {
        open = !open;
    }

    public int width() {
        return LABEL_WIDTH + MODE_WIDTH + columns.length * CELL_WIDTH + 8;
    }

    public int height() {
        return HEADER_HEIGHT + 6 * ROW_HEIGHT + 4;
    }

    /** {@code bits}: face index 0..5 -> raw synced face-config bits. */
    public void render(GuiGraphics gui, Font font, int x, int y, IntUnaryOperator bits) {
        if (!open) {
            return;
        }
        gui.fill(x, y, x + width(), y + height(), 0xE8101418);
        int cx = x + 4 + LABEL_WIDTH + MODE_WIDTH;
        for (Column column : columns) {
            gui.drawString(font, Component.translatable(column.labelKey()),
                    cx + 2, y + 2, 0xFF9AD9E0, false);
            cx += CELL_WIDTH;
        }
        for (int face = 0; face < 6; face++) {
            int rowY = y + HEADER_HEIGHT + face * ROW_HEIGHT;
            int faceBits = bits.applyAsInt(face);
            boolean manual = FaceConfig.isManualBits(faceBits);
            gui.drawString(font, Component.translatable(FACE_KEYS[face]),
                    x + 4, rowY + 3, 0xFFE0E6E8, false);
            int modeX = x + 4 + LABEL_WIDTH;
            gui.fill(modeX, rowY + 1, modeX + MODE_WIDTH - 2, rowY + ROW_HEIGHT - 1,
                    manual ? 0xFF2E4A6B : 0xFF2A2F33);
            gui.drawString(font, Component.translatable(manual
                            ? "gui.krios_industry.faces.manual"
                            : FaceConfig.isConfiguredBits(faceBits)
                                    ? "gui.krios_industry.faces.auto"
                                    : "gui.krios_industry.faces.vanilla"),
                    modeX + 2, rowY + 3, manual ? 0xFFFFFFFF : 0xFF9FA6AA, false);
            int cellX = modeX + MODE_WIDTH;
            for (Column column : columns) {
                if (manual) {
                    boolean allowed = FaceConfig.allowsBits(faceBits, column.flow(), column.out());
                    gui.fill(cellX, rowY + 1, cellX + CELL_WIDTH - 2, rowY + ROW_HEIGHT - 1,
                            allowed ? 0xFF2F6B3A : 0xFF6B2F2F);
                    gui.drawCenteredString(font, allowed ? "✔" : "✘",
                            cellX + (CELL_WIDTH - 2) / 2, rowY + 3, 0xFFFFFFFF);
                } else if (FaceConfig.isConfiguredBits(faceBits)
                        && FaceConfig.allowsBits(faceBits, column.flow(), column.out())) {
                    // Auto routing record, informational only.
                    gui.drawCenteredString(font, "•",
                            cellX + (CELL_WIDTH - 2) / 2, rowY + 3, 0xFF9AD9E0);
                }
                cellX += CELL_WIDTH;
            }
        }
    }

    /** Returns the menu button id for a click inside the panel, or -1. */
    public int click(int x, int y, double mouseX, double mouseY, IntUnaryOperator bits) {
        if (!open || mouseX < x || mouseX >= x + width() || mouseY < y + HEADER_HEIGHT
                || mouseY >= y + HEADER_HEIGHT + 6 * ROW_HEIGHT) {
            return -1;
        }
        int face = (int) ((mouseY - y - HEADER_HEIGHT) / ROW_HEIGHT);
        int modeX = x + 4 + LABEL_WIDTH;
        if (mouseX >= modeX && mouseX < modeX + MODE_WIDTH - 2) {
            return face * 16 + 15;
        }
        if (!FaceConfig.isManualBits(bits.applyAsInt(face))) {
            return -1;
        }
        int cellX = modeX + MODE_WIDTH;
        for (Column column : columns) {
            if (mouseX >= cellX && mouseX < cellX + CELL_WIDTH - 2) {
                return face * 16 + column.sub();
            }
            cellX += CELL_WIDTH;
        }
        return -1;
    }
}
