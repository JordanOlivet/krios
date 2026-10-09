package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.lure.QuantumLureBlockEntity;
import io.lakio.krios.industry.machine.lure.QuantumLureMenu;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Quantum Lure terminal (design 2026-10-09): one vertical gauge per line
 * in the input column (only the effective tier's lines), a 5x5 water map
 * in the center — each cell lit when it can fish, the machine mark below
 * as on the planting map — with the open-water readout and the hologram
 * eye under it, 3x3 catch buffer on the right.
 */
public class QuantumLureScreen extends ZoneMachineScreen<QuantumLureMenu> {

    private static final int GAUGE_W = 5;
    private static final int GAUGE_GAP = 2;
    private static final int GAUGE_TOP = ZoneMachineMenu.SLOT_TOP_Y;
    /** Same span as the three-slot column it replaces (y 26..80). */
    private static final int GAUGE_H = 54;
    /** Gauges center on the old input column's middle. */
    private static final int GAUGE_CENTER_X = 55;

    private static final int S = ZoneMachineMenu.MAP_SIZE;
    private static final int CELL = 8;
    private static final int EYE_X = ZoneMachineMenu.MAP_X + S - 4 + BTN + 6;

    public QuantumLureScreen(QuantumLureMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected String voiceKey() {
        return "quantum_lure";
    }

    @Override
    protected StatsPanel createStatsPanel() {
        return new StatsPanel(
                new StatsPanel.Row(StatsPanel.Icon.SPEED,
                        () -> Component.literal("×" + String.format("%.1f",
                                (2 + modulePoints(ModuleType.SPEED)) / 2.0)),
                        Component.translatable("gui.krios_industry.stats.catch_pace")),
                new StatsPanel.Row(StatsPanel.Icon.LURE,
                        () -> Component.literal("-" + modulePoints(ModuleType.LURE)
                                * KoiosBalance.LURE_TICKS_PER_POINT / 20 + " s"),
                        Component.translatable("gui.krios_industry.stats.lure")),
                new StatsPanel.Row(StatsPanel.Icon.LUCK,
                        () -> Component.literal("+" + modulePoints(ModuleType.LUCK)),
                        Component.translatable("gui.krios_industry.stats.luck")),
                new StatsPanel.Row(StatsPanel.Icon.ENERGY_MULT,
                        () -> Component.literal("×" + String.format("%.2f",
                                KoiosBalance.efficiencyPermille(modulePoints(ModuleType.EFFICIENCY)) / 1000.0)),
                        Component.translatable("gui.krios_industry.stats.energy")),
                new StatsPanel.Row(StatsPanel.Icon.YIELD,
                        () -> Component.literal("+" + modulePoints(ModuleType.YIELD) * 25 + " %"),
                        Component.translatable("gui.krios_industry.stats.yield")),
                new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                        () -> Component.literal(menu.drawPerTick() + " E/t"),
                        Component.translatable("gui.krios_industry.stats.consumption")));
    }

    // --- Work area ---

    private int gaugeX(int line) {
        int lines = menu.lines();
        int width = lines * GAUGE_W + (lines - 1) * GAUGE_GAP;
        return GAUGE_CENTER_X - width / 2 + line * (GAUGE_W + GAUGE_GAP);
    }

    @Override
    protected void renderWork(GuiGraphics gui) {
        boolean water = menu.waterStatus() != QuantumLureBlockEntity.WATER_NONE;
        for (int line = 0; line < menu.lines(); line++) {
            drawGauge(gui, leftPos + gaugeX(line), topPos + GAUGE_TOP, water ? menu.lineProgress(line) : -1);
        }
        drawWaterMap(gui);
        int y = topPos + ZoneMachineMenu.CONTROLS_Y;
        int cells = Integer.bitCount(menu.waterMask());
        gui.drawCenteredString(font, cells + "/" + QuantumLureBlockEntity.ZONE_CELLS,
                leftPos + ZoneMachineMenu.MAP_X + S / 2, y + 1, 0xFF000000 | waterColor());
        renderEye(gui, EYE_X, ZoneMachineMenu.CONTROLS_Y);
    }

    private int waterColor() {
        return switch (menu.waterStatus()) {
            case QuantumLureBlockEntity.WATER_OPEN -> COLOR_ACCENT;
            case QuantumLureBlockEntity.WATER_PARTIAL -> COLOR_ENERGY;
            default -> COLOR_ERROR;
        };
    }

    /**
     * Vertical line gauge, filling bottom-up toward the catch; a bright
     * leading edge as on the energy bar. {@code permille} < 0 = line out
     * of the water (dim skeleton).
     */
    private static void drawGauge(GuiGraphics gui, int x, int y, int permille) {
        gui.fill(x - 1, y - 1, x + GAUGE_W + 1, y + GAUGE_H + 1, 0xFF0C0E13);
        gui.fill(x, y, x + GAUGE_W, y + GAUGE_H, permille < 0 ? 0xFF1B202A : 0xFF262B35);
        int fill = permille <= 0 ? 0 : Math.min(GAUGE_H, permille * GAUGE_H / 1000);
        if (fill > 0) {
            gui.fill(x, y + GAUGE_H - fill, x + GAUGE_W, y + GAUGE_H, 0xFF31D8FF);
            gui.fill(x, y + GAUGE_H - fill, x + GAUGE_W, y + GAUGE_H - fill + 1, 0xFF9FF0FF);
        }
    }

    /**
     * The 5x5 zone seen from the screen: the bottom row lies against the
     * machine (amber mark below), rows go away from it, columns run left
     * to right as the player standing at the screen sees them — the same
     * order ZoneGeometry sweeps cells.
     */
    private void drawWaterMap(GuiGraphics gui) {
        int x = leftPos + ZoneMachineMenu.MAP_X;
        int y = topPos + ZoneMachineMenu.MAP_Y;
        gui.fill(x - 1, y - 1, x + S + 1, y + S + 1, 0xFF0C0E13);
        gui.fill(x, y, x + S, y + S, 0xFF12151B);
        int edge = QuantumLureBlockEntity.ZONE_EDGE;
        int span = edge * CELL + (edge - 1);
        int origin = (S - span) / 2;
        int mask = menu.waterMask();
        for (int i = 0; i < QuantumLureBlockEntity.ZONE_CELLS; i++) {
            int row = i / edge;
            int col = i % edge;
            int cx = x + origin + col * (CELL + 1);
            int cy = y + S - origin - (row + 1) * CELL - row;
            boolean ok = (mask & (1 << i)) != 0;
            gui.fill(cx, cy, cx + CELL, cy + CELL, ok ? 0xFF1C6A86 : 0xFF2A1D22);
            if (ok) {
                // Surface glint.
                gui.fill(cx + 1, cy + 2, cx + 4, cy + 3, 0xFF5FD0F0);
            }
        }
        // The machine, just below the map's bottom edge.
        gui.fill(x + S / 2 - 3, y + S + 2, x + S / 2 + 3, y + S + 4, 0xFF000000 | COLOR_ENERGY);
    }

    private int gaugeAt(double mouseX, double mouseY) {
        for (int line = 0; line < menu.lines(); line++) {
            if (hit(mouseX, mouseY, gaugeX(line) - 1, GAUGE_TOP - 1, GAUGE_W + 2, GAUGE_H + 2)) {
                return line;
            }
        }
        return -1;
    }

    private boolean eyeHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, EYE_X, ZoneMachineMenu.CONTROLS_Y, EYE_W, BTN);
    }

    private boolean waterHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, ZoneMachineMenu.MAP_X, ZoneMachineMenu.MAP_Y, S, S)
                || hit(mouseX, mouseY, ZoneMachineMenu.MAP_X, ZoneMachineMenu.CONTROLS_Y, S, BTN);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        int line = gaugeAt(mouseX, mouseY);
        if (line >= 0) {
            boolean water = menu.waterStatus() != QuantumLureBlockEntity.WATER_NONE;
            gui.renderTooltip(font, water
                    ? Component.translatable("gui.krios_industry.lure.line", line + 1, menu.lineProgress(line) / 10)
                    : Component.translatable("gui.krios_industry.lure.line_idle", line + 1), mouseX, mouseY);
        } else if (eyeHit(mouseX, mouseY)) {
            renderEyeTooltip(gui, mouseX, mouseY);
        } else if (waterHit(mouseX, mouseY)) {
            String key = switch (menu.waterStatus()) {
                case QuantumLureBlockEntity.WATER_OPEN -> "open";
                case QuantumLureBlockEntity.WATER_PARTIAL -> "partial";
                default -> "none";
            };
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.krios_industry.lure.water_" + key)
                            .withStyle(menu.waterStatus() == QuantumLureBlockEntity.WATER_OPEN
                                    ? ChatFormatting.AQUA : ChatFormatting.GOLD),
                    Component.translatable("gui.krios_industry.lure.water_hint")
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && eyeHit(mouseX, mouseY)) {
            toggleEye();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- Voice ---

    @Override
    protected Component workingPhrase() {
        if (menu.stateCode() == ZoneMachineBlockEntity.STATE_WORKING
                && menu.waterStatus() == QuantumLureBlockEntity.WATER_PARTIAL) {
            return Component.translatable("gui.krios_industry.voice.quantum_lure.working_partial");
        }
        return super.workingPhrase();
    }

    @Override
    protected Component idlePhrase(int stateCode) {
        return stateCode == QuantumLureBlockEntity.STATE_NO_WATER
                ? Component.translatable("gui.krios_industry.voice.quantum_lure.no_water") : null;
    }
}
