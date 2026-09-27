package io.lakio.krios.industry.client.screen;

import java.util.List;
import java.util.function.Supplier;

import io.lakio.krios.industry.client.ClientPrefs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Collapsible stats side panel, docked LEFT of a terminal screen (art
 * direction: the panel keeps machine stats out of the working area). One
 * row per stat: pixel icon + live value, full stat name as tooltip. The
 * collapsed/expanded state persists globally through {@link ClientPrefs}.
 */
public class StatsPanel {

    /** One stat row: icon, live value supplier, tooltip naming the stat. */
    public record Row(Icon icon, Supplier<Component> value, Component tooltip) {
    }

    public enum Icon { SPEED, ENERGY_MULT, YIELD, OUTPUT, STORED }

    private static final int ROW_HEIGHT = 14;
    private static final int TAB_SIZE = 12;
    private static final int PAD = 4;
    private static final int ICON_SPACE = 14;

    private final List<Row> rows;
    private boolean expanded = ClientPrefs.statsExpanded();

    public StatsPanel(Row... rows) {
        this.rows = List.of(rows);
    }

    private int panelWidth(Font font) {
        int max = 0;
        for (Row row : rows) {
            max = Math.max(max, font.width(row.value().get()));
        }
        return PAD + ICON_SPACE + max + PAD;
    }

    private int tabX(int leftPos) {
        return leftPos - TAB_SIZE - 2;
    }

    private int panelX(int leftPos, Font font) {
        return leftPos - panelWidth(font) - 2;
    }

    public void render(GuiGraphics gui, Font font, int leftPos, int topPos, int mouseX, int mouseY) {
        int tx = tabX(leftPos);
        int ty = topPos + 4;
        gui.fill(tx, ty, tx + TAB_SIZE, ty + TAB_SIZE, expanded ? 0xFF0A6E87 : 0xFF262B35);
        for (int i = 0; i < 3; i++) {
            gui.fill(tx + 3, ty + 3 + i * 3, tx + TAB_SIZE - 3, ty + 4 + i * 3, 0xFFE8EAF0);
        }
        if (expanded) {
            int width = panelWidth(font);
            int px = panelX(leftPos, font);
            int py = topPos + 18;
            int height = PAD + rows.size() * ROW_HEIGHT + PAD - 4;
            gui.fill(px, py, px + width, py + height, 0xE8101418);
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int rowY = py + PAD + i * ROW_HEIGHT;
                drawIcon(gui, row.icon(), px + PAD, rowY);
                Component value = row.value().get();
                gui.drawString(font, value, px + width - PAD - font.width(value), rowY + 1, 0xFFE8EAF0, false);
                if (mouseX >= px && mouseX < px + width && mouseY >= rowY - 2 && mouseY < rowY + ROW_HEIGHT - 2) {
                    gui.renderTooltip(font, row.tooltip(), mouseX, mouseY);
                }
            }
        }
        if (mouseX >= tx && mouseX < tx + TAB_SIZE && mouseY >= ty && mouseY < ty + TAB_SIZE) {
            gui.renderTooltip(font, Component.translatable("gui.krios_industry.stats.title"), mouseX, mouseY);
        }
    }

    /** True when the click was on the collapse tab (state toggled + saved). */
    public boolean click(int leftPos, int topPos, double mouseX, double mouseY) {
        int tx = tabX(leftPos);
        int ty = topPos + 4;
        if (mouseX >= tx && mouseX < tx + TAB_SIZE && mouseY >= ty && mouseY < ty + TAB_SIZE) {
            expanded = !expanded;
            ClientPrefs.setStatsExpanded(expanded);
            return true;
        }
        return false;
    }

    private static void drawIcon(GuiGraphics gui, Icon icon, int x, int y) {
        int cyan = 0xFF31D8FF;
        int amber = 0xFFFFB03A;
        switch (icon) {
            case SPEED -> {
                for (int d = 0; d < 4; d++) {
                    gui.fill(x + d, y + 1 + d, x + d + 2, y + 2 + d, cyan);
                    gui.fill(x + d, y + 7 - d, x + d + 2, y + 8 - d, cyan);
                    gui.fill(x + 4 + d, y + 1 + d, x + 4 + d + 2, y + 2 + d, cyan);
                    gui.fill(x + 4 + d, y + 7 - d, x + 4 + d + 2, y + 8 - d, cyan);
                }
            }
            case ENERGY_MULT, OUTPUT -> {
                int color = icon == Icon.OUTPUT ? amber : cyan;
                gui.fill(x + 5, y, x + 8, y + 1, color);
                gui.fill(x + 4, y + 1, x + 7, y + 2, color);
                gui.fill(x + 3, y + 2, x + 6, y + 3, color);
                gui.fill(x + 2, y + 3, x + 8, y + 4, color);
                gui.fill(x + 3, y + 4, x + 7, y + 5, color);
                gui.fill(x + 2, y + 5, x + 5, y + 6, color);
                gui.fill(x + 1, y + 6, x + 4, y + 7, color);
                gui.fill(x, y + 7, x + 3, y + 9, color);
            }
            case YIELD -> {
                gui.fill(x + 3, y, x + 6, y + 9, cyan);
                gui.fill(x, y + 3, x + 9, y + 6, cyan);
            }
            case STORED -> {
                gui.fill(x, y + 1, x + 9, y + 8, amber);
                gui.fill(x + 1, y + 2, x + 8, y + 7, 0xFF10141B);
                gui.fill(x + 9, y + 3, x + 10, y + 6, amber);
                gui.fill(x + 1, y + 2, x + 5, y + 7, amber);
            }
        }
    }
}
