package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.zone.PlantingZoneBlockEntity;
import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import io.lakio.krios.industry.machine.zone.PlantingZoneMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Planting-machine terminal (option A mockup, 2026-10-08 — Chrono Farmer,
 * Cyclic Forester): plant column, a holographic zone map (outer frame =
 * the tier maximum, cyan square = the current zone, amber mark = the
 * machine, so the zone grows "upward" from it — away from the player
 * standing at the screen), -/+ one ring per click, the hologram eye, and
 * the warned voiding toggle. 3x3 harvest buffer on the right.
 */
public abstract class PlantingZoneScreen<T extends PlantingZoneMenu> extends ZoneMachineScreen<T> {

    private static final int S = ZoneMachineMenu.MAP_SIZE;
    // Buttons sit slightly outside the map so a "27×27" label clears them.
    private static final int MINUS_X = ZoneMachineMenu.MAP_X - 6;
    private static final int PLUS_X = ZoneMachineMenu.MAP_X + S - 4;
    private static final int EYE_X = PLUS_X + BTN + 6;
    // Seed-voiding toggle in the free column left of the buffer (the
    // buffer's last row reaches y=80, no room below it), aligned with its
    // top; the warning sign sits right under the toggle.
    private static final int VOID_W = 12;
    private static final int VOID_X = ZoneMachineMenu.BUFFER_X - 1 - 4 - VOID_W;
    private static final int VOID_Y = ZoneMachineMenu.SLOT_TOP_Y - 1;
    private static final int WARN_W = 11;
    private static final int WARN_X = VOID_X;
    private static final int WARN_Y = VOID_Y + BTN + 4;

    protected PlantingZoneScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** Lang segment of the plantable vocabulary (seeds / saplings). */
    protected abstract String plantableKey();

    @Override
    protected StatsPanel createStatsPanel() {
        return new StatsPanel(
                // Growth pace: the same x(2+pts)/2 as every machine's speed.
                new StatsPanel.Row(StatsPanel.Icon.SPEED,
                        () -> Component.literal("×" + String.format("%.1f",
                                (2 + modulePoints(ModuleType.SPEED)) / 2.0)),
                        Component.translatable("gui.krios_industry.stats.growth")),
                new StatsPanel.Row(StatsPanel.Icon.ENERGY_MULT,
                        () -> Component.literal("×" + String.format("%.2f",
                                KoiosBalance.efficiencyPermille(modulePoints(ModuleType.EFFICIENCY)) / 1000.0)),
                        Component.translatable("gui.krios_industry.stats.energy")),
                new StatsPanel.Row(StatsPanel.Icon.YIELD,
                        () -> Component.literal("+" + modulePoints(ModuleType.YIELD) * 25 + " %"),
                        Component.translatable("gui.krios_industry.stats.yield")),
                new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                        () -> Component.literal(menu.drawPerTick() + " E/t"),
                        Component.translatable("gui.krios_industry.stats.consumption")),
                new StatsPanel.Row(StatsPanel.Icon.ZONE,
                        () -> Component.literal(menu.workableCells() < 0 ? "—"
                                : Integer.toString(menu.workableCells())),
                        Component.translatable("gui.krios_industry.stats.cells")));
    }

    // --- Work area ---

    @Override
    protected void renderWork(GuiGraphics gui) {
        drawMap(gui);
        int y = topPos + ZoneMachineMenu.CONTROLS_Y;
        int minusX = leftPos + MINUS_X;
        int plusX = leftPos + PLUS_X;
        boolean canShrink = menu.zoneEdge() > 1;
        boolean canGrow = menu.zoneEdge() < menu.zoneMaxEdge();
        drawButton(gui, minusX, y, BTN, canShrink);
        int glyph = canShrink ? 0xFFE8EAF0 : 0xFF5A6170;
        gui.fill(minusX + 2, y + 4, minusX + 8, y + 6, glyph);
        drawButton(gui, plusX, y, BTN, canGrow);
        glyph = canGrow ? 0xFFE8EAF0 : 0xFF5A6170;
        gui.fill(plusX + 2, y + 4, plusX + 8, y + 6, glyph);
        gui.fill(plusX + 4, y + 2, plusX + 6, y + 8, glyph);
        String label = menu.zoneEdge() + "×" + menu.zoneEdge();
        gui.drawCenteredString(font, label, leftPos + ZoneMachineMenu.MAP_X + S / 2, y + 1, 0xFFE8EAF0);
        renderEye(gui, EYE_X, ZoneMachineMenu.CONTROLS_Y);
        drawVoidToggle(gui, leftPos + VOID_X, topPos + VOID_Y, menu.voidSurplus());
        if (menu.voidSurplus()) {
            drawWarning(gui, leftPos + WARN_X, topPos + WARN_Y);
        }
    }

    /** Seed glyph; struck through in red while surplus seeds are destroyed. */
    private static void drawVoidToggle(GuiGraphics gui, int x, int y, boolean on) {
        drawButton(gui, x, y, VOID_W, true);
        int seed = on ? 0xFF5FE09A : 0xFF2A7A52;
        gui.fill(x + 5, y + 2, x + 7, y + 3, seed);
        gui.fill(x + 4, y + 3, x + 8, y + 7, seed);
        gui.fill(x + 5, y + 7, x + 7, y + 8, seed);
        if (on) {
            int red = 0xFF000000 | COLOR_ERROR;
            for (int i = 0; i < 6; i++) {
                gui.fill(x + 3 + i, y + 7 - i, x + 4 + i, y + 8 - i, red);
            }
        }
    }

    /**
     * Holographic zone map: the frame is the effective tier's maximum, the
     * zone grows from the machine mark at the bottom edge (the zone starts
     * right in front of the machine and stays adjacent when shrunk).
     */
    private void drawMap(GuiGraphics gui) {
        int x = leftPos + ZoneMachineMenu.MAP_X;
        int y = topPos + ZoneMachineMenu.MAP_Y;
        gui.fill(x - 1, y - 1, x + S + 1, y + S + 1, 0xFF0C0E13);
        gui.fill(x, y, x + S, y + S, 0xFF12151B);
        // Tier maximum: dim frame.
        int dim = 0xFF000000 | COLOR_ACCENT_DIM;
        gui.fill(x, y, x + S, y + 1, dim);
        gui.fill(x, y + S - 1, x + S, y + S, dim);
        gui.fill(x, y, x + 1, y + S, dim);
        gui.fill(x + S - 1, y, x + S, y + S, dim);

        int max = menu.zoneMaxEdge();
        int edge = Math.min(menu.zoneEdge(), max);
        int px = Math.max(3, Math.round(edge * (float) S / max));
        int left = x + (S - px) / 2;
        int bottom = y + S;
        int top = bottom - px;
        gui.fill(left, top, left + px, bottom, 0x3031D8FF);
        // Cell grid when cells are big enough to read.
        float cell = px / (float) edge;
        if (cell >= 3) {
            for (int i = 1; i < edge; i++) {
                int off = Math.round(i * cell);
                gui.fill(left + off, top, left + off + 1, bottom, 0x3031D8FF);
                gui.fill(left, top + off, left + px, top + off + 1, 0x3031D8FF);
            }
        }
        int accent = 0xFF000000 | COLOR_ACCENT;
        gui.fill(left, top, left + px, top + 1, accent);
        gui.fill(left, bottom - 1, left + px, bottom, accent);
        gui.fill(left, top, left + 1, bottom, accent);
        gui.fill(left + px - 1, top, left + px, bottom, accent);
        // The machine, just below the map's bottom edge.
        gui.fill(x + S / 2 - 3, y + S + 2, x + S / 2 + 3, y + S + 4, 0xFF000000 | COLOR_ENERGY);
    }

    private boolean minusHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, MINUS_X, ZoneMachineMenu.CONTROLS_Y, BTN, BTN);
    }

    private boolean plusHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, PLUS_X, ZoneMachineMenu.CONTROLS_Y, BTN, BTN);
    }

    private boolean eyeHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, EYE_X, ZoneMachineMenu.CONTROLS_Y, EYE_W, BTN);
    }

    private boolean voidHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, VOID_X, VOID_Y, VOID_W, BTN);
    }

    private boolean warnHit(double mouseX, double mouseY) {
        return menu.voidSurplus()
                && hit(mouseX, mouseY, WARN_X, WARN_Y, WARN_W, BTN);
    }

    private boolean mapHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, ZoneMachineMenu.MAP_X, ZoneMachineMenu.MAP_Y, S, S);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        if (minusHit(mouseX, mouseY)) {
            gui.renderTooltip(font, Component.translatable("gui.krios_industry.zone.shrink"), mouseX, mouseY);
        } else if (plusHit(mouseX, mouseY)) {
            gui.renderTooltip(font, Component.translatable("gui.krios_industry.zone.grow",
                    menu.zoneMaxEdge(), menu.zoneMaxEdge()), mouseX, mouseY);
        } else if (eyeHit(mouseX, mouseY)) {
            renderEyeTooltip(gui, mouseX, mouseY);
        } else if (voidHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable(menu.voidSurplus()
                            ? "gui.krios_industry." + plantableKey() + ".void_on" : "gui.krios_industry." + plantableKey() + ".void_off"),
                    Component.translatable("gui.krios_industry." + plantableKey() + ".refill")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (warnHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.krios_industry." + plantableKey() + ".warning_title")
                            .withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable("gui.krios_industry." + plantableKey() + ".warning")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (mapHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.krios_industry.zone.map",
                            menu.zoneEdge(), menu.zoneEdge(), menu.zoneMaxEdge(), menu.zoneMaxEdge()),
                    Component.translatable("gui.krios_industry.zone.map_front")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (minusHit(mouseX, mouseY) && menu.zoneEdge() > 1) {
                clickButton(ZoneMachineBlockEntity.BUTTON_ZONE_SHRINK);
                return true;
            }
            if (plusHit(mouseX, mouseY) && menu.zoneEdge() < menu.zoneMaxEdge()) {
                clickButton(ZoneMachineBlockEntity.BUTTON_ZONE_GROW);
                return true;
            }
            if (voidHit(mouseX, mouseY)) {
                clickButton(PlantingZoneBlockEntity.BUTTON_TOGGLE_VOID);
                return true;
            }
            if (eyeHit(mouseX, mouseY)) {
                toggleEye();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected Component idlePhrase(int stateCode) {
        String base = "gui.krios_industry.voice." + voiceKey() + ".";
        return switch (stateCode) {
            case PlantingZoneBlockEntity.STATE_NO_PLANTS -> Component.translatable(base + "no_seeds");
            case PlantingZoneBlockEntity.STATE_EMPTY_ZONE -> Component.translatable(base + "empty_zone");
            default -> menu.workableCells() > 0 ? Component.translatable(base + "waiting") : null;
        };
    }
}
