package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.zone.PlantingZoneBlockEntity;
import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import io.lakio.krios.industry.machine.zone.PlantingZoneMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Planting-machine terminal (option A mockup, 2026-10-08 — Chrono Farmer,
 * Cyclic Forester): plant column, the shared zone map and controls, the
 * warned voiding toggle, 3x3 harvest buffer on the right.
 */
public abstract class PlantingZoneScreen<T extends PlantingZoneMenu> extends ZoneMachineScreen<T> {

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
        renderZoneControls(gui);
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

    private boolean voidHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, VOID_X, VOID_Y, VOID_W, BTN);
    }

    private boolean warnHit(double mouseX, double mouseY) {
        return menu.voidSurplus()
                && hit(mouseX, mouseY, WARN_X, WARN_Y, WARN_W, BTN);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        if (renderZoneTooltip(gui, mouseX, mouseY)) {
            return;
        }
        if (voidHit(mouseX, mouseY)) {
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
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (clickZoneControls(mouseX, mouseY)) {
                return true;
            }
            if (voidHit(mouseX, mouseY)) {
                clickButton(PlantingZoneBlockEntity.BUTTON_TOGGLE_VOID);
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
