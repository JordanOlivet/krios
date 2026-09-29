package io.lakio.krios.industry.client.screen;

import java.util.List;
import java.util.function.Supplier;

import io.lakio.krios.industry.client.ClientPrefs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Machine stats annex (art direction: stats stay out of the working area),
 * docked LEFT on the shared {@link AnnexPanel} chassis. One row per stat:
 * pixel icon + live value, full stat name as tooltip. The open state
 * persists globally through {@link ClientPrefs}.
 */
public class StatsPanel extends AnnexPanel {

    /** One stat row: icon, live value supplier, tooltip naming the stat. */
    public record Row(Icon icon, Supplier<Component> value, Component tooltip) {
    }

    public enum Icon { SPEED, ENERGY_MULT, YIELD, OUTPUT, STORED }

    private static final int ROW_HEIGHT = 14;
    private static final int ICON_SPACE = 14;

    private final List<Row> rows;
    // Sized against the live values each frame (layout hook); clicks always
    // land after a render, so the cache is fresh when it matters.
    private int cachedWidth = 50;

    public StatsPanel(Row... rows) {
        super(Side.LEFT, 4, "gui.krios_industry.stats.title", ClientPrefs.statsExpanded());
        this.rows = List.of(rows);
    }

    @Override
    protected void onToggle(boolean open) {
        ClientPrefs.setStatsExpanded(open);
    }

    @Override
    protected void layout(Font font) {
        int max = 0;
        for (Row row : rows) {
            max = Math.max(max, font.width(row.value().get()));
        }
        cachedWidth = ICON_SPACE + max;
    }

    @Override
    protected int contentWidth() {
        return cachedWidth;
    }

    @Override
    protected int contentHeight() {
        return rows.size() * ROW_HEIGHT;
    }

    @Override
    protected void renderContent(GuiGraphics gui, Font font, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = y + 2 + i * ROW_HEIGHT;
            drawIcon(gui, row.icon(), x, rowY);
            Component value = row.value().get();
            gui.drawString(font, value, x + cachedWidth - font.width(value), rowY + 1, 0xFFE8EAF0, false);
            if (mouseX >= x && mouseX < x + cachedWidth && mouseY >= rowY - 2 && mouseY < rowY + ROW_HEIGHT - 2) {
                gui.renderTooltip(font, row.tooltip(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void drawTabIcon(GuiGraphics gui, int x, int y) {
        // Three data rows, the panel's function at a glance.
        for (int i = 0; i < 3; i++) {
            gui.fill(x, y + 1 + i * 3, x + 8, y + 2 + i * 3, 0xFFE8EAF0);
        }
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
