package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.client.ClientPrefs;
import io.lakio.krios.industry.machine.zone.CreatureZoneBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Census annex of the creature machines (Harvest Pod design 2026-10-13,
 * user: one component and one row format for every creature machine),
 * docked RIGHT on the shared {@link AnnexPanel} chassis, open by default.
 * One row per species: name, small pixel marks (what the machine does
 * with it, red when an input is missing), and a value right-aligned; the
 * machine supplies rows and tooltips. Past the synced rows, a "+n others"
 * line. The open state persists globally through {@link ClientPrefs}.
 */
public class CensusPanel extends AnnexPanel {

    public enum Glyph { SHEARS, BUCKET, BOWL, TIMER }

    /** A mark beside the value; {@code missing} draws it in red. */
    public record Mark(Glyph glyph, boolean missing) {
    }

    public record Row(Component name, String value, int valueColor, List<Mark> marks, List<Component> tooltip) {
    }

    /** What the machine shows; rows past its census are null. */
    public interface Source {
        @Nullable
        Row row(int index);

        /** Species in the zone, census rows included. */
        int speciesTotal();
    }

    private static final int ROWS = CreatureZoneBlockEntity.CENSUS_ROWS;
    private static final int ROW_H = 11;
    private static final int WIDTH = 124;
    private static final int MARK_W = 10;

    private final Source source;

    public CensusPanel(Source source) {
        super(Side.RIGHT, "gui.krios_industry.census.title", ClientPrefs.censusOpen());
        this.source = source;
    }

    @Override
    protected void onToggle(boolean open) {
        ClientPrefs.setCensusOpen(open);
    }

    @Override
    protected int contentWidth() {
        return WIDTH;
    }

    @Override
    protected int contentHeight() {
        // Fixed: the stack below never jumps as species come and go.
        return (ROWS + 1) * ROW_H + 2;
    }

    @Override
    protected void renderContent(GuiGraphics gui, Font font, int x, int y, int mouseX, int mouseY) {
        int width = effectiveContentWidth();
        int shown = 0;
        List<Component> tooltip = null;
        for (int i = 0; i < ROWS; i++) {
            Row row = source.row(i);
            if (row == null) {
                continue;
            }
            int rowY = y + 3 + shown * ROW_H;
            shown++;
            int valueW = font.width(row.value());
            int marksX = x + width - valueW - 3 - row.marks().size() * MARK_W;
            String name = font.plainSubstrByWidth(row.name().getString(), marksX - x - 3);
            gui.drawString(font, name, x, rowY, 0xFF000000 | KriosTerminalScreen.COLOR_TEXT_DIM, false);
            for (int m = 0; m < row.marks().size(); m++) {
                Mark mark = row.marks().get(m);
                drawGlyph(gui, mark.glyph(), marksX + m * MARK_W, rowY - 1, mark.missing());
            }
            gui.drawString(font, row.value(), x + width - valueW, rowY, row.valueColor(), false);
            if (mouseX >= x && mouseX < x + width && mouseY >= rowY - 2 && mouseY < rowY - 2 + ROW_H) {
                tooltip = row.tooltip();
            }
        }
        int others = source.speciesTotal() - shown;
        int lastY = y + 3 + shown * ROW_H;
        if (shown == 0) {
            gui.drawString(font, Component.translatable("gui.krios_industry.census.empty"), x, lastY,
                    0xFF000000 | KriosTerminalScreen.COLOR_TEXT_DIM, false);
        } else if (others > 0) {
            gui.drawString(font, Component.translatable("gui.krios_industry.census.others", others), x, lastY,
                    0xFF000000 | KriosTerminalScreen.COLOR_TEXT_DIM, false);
        }
        if (tooltip != null) {
            gui.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void drawTabIcon(GuiGraphics gui, int x, int y) {
        // Three heads in a row: a herd.
        int c = 0xFFE8EAF0;
        for (int i = 0; i < 3; i++) {
            gui.fill(x + i * 3, y + 2, x + i * 3 + 2, y + 4, c);
            gui.fill(x + i * 3, y + 5, x + i * 3 + 2, y + 8, c);
        }
    }

    /** 8x8 marks at (x, y). */
    private static void drawGlyph(GuiGraphics gui, Glyph glyph, int x, int y, boolean missing) {
        int red = 0xFF000000 | KriosTerminalScreen.COLOR_ERROR;
        switch (glyph) {
            case SHEARS -> {
                int c = missing ? red : 0xFFC9D1DC;
                for (int d = 0; d < 5; d++) {
                    gui.fill(x + 1 + d, y + 1 + d, x + 2 + d, y + 2 + d, c);
                    gui.fill(x + 6 - d, y + 1 + d, x + 7 - d, y + 2 + d, c);
                }
                gui.fill(x, y + 6, x + 2, y + 8, 0xFF000000 | KriosTerminalScreen.COLOR_ENERGY);
                gui.fill(x + 6, y + 6, x + 8, y + 8, 0xFF000000 | KriosTerminalScreen.COLOR_ENERGY);
            }
            case BUCKET -> {
                int c = missing ? red : 0xFFB8C0CC;
                gui.fill(x, y + 1, x + 8, y + 2, c);
                gui.fill(x + 1, y + 2, x + 7, y + 7, c);
                gui.fill(x + 2, y + 7, x + 6, y + 8, c);
                gui.fill(x + 2, y + 2, x + 6, y + 4, missing ? 0xFF10141B : 0xFFF2F2F2);
            }
            case BOWL -> {
                int c = missing ? red : 0xFF9A6A3A;
                gui.fill(x, y + 3, x + 8, y + 5, c);
                gui.fill(x + 1, y + 5, x + 7, y + 6, c);
                gui.fill(x + 2, y + 6, x + 6, y + 7, c);
                gui.fill(x + 1, y + 3, x + 7, y + 4, missing ? 0xFF10141B : 0xFFC0504D);
            }
            case TIMER -> {
                int c = 0xFF000000 | KriosTerminalScreen.COLOR_ACCENT;
                gui.fill(x + 2, y, x + 6, y + 1, c);
                gui.fill(x + 1, y + 1, x + 2, y + 7, c);
                gui.fill(x + 6, y + 1, x + 7, y + 7, c);
                gui.fill(x + 2, y + 7, x + 6, y + 8, c);
                gui.fill(x, y + 2, x + 1, y + 6, c);
                gui.fill(x + 7, y + 2, x + 8, y + 6, c);
                gui.fill(x + 4, y + 2, x + 5, y + 5, c);
                gui.fill(x + 4, y + 4, x + 6, y + 5, c);
            }
        }
    }
}
