package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.pod.BreedingPodBlockEntity;
import io.lakio.krios.industry.machine.pod.BreedingPodMenu;
import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;

/**
 * Breeding Pod terminal (design 2026-10-09): food column, the shared zone
 * map and controls, and where the buffer would be, a census of the zone —
 * top species with count/cap (amber at the cap), "+n others" past five —
 * above the population cap selector (shift: ±8).
 */
public class BreedingPodScreen extends ZoneMachineScreen<BreedingPodMenu> {

    private static final int CENSUS_X = 172;
    private static final int CENSUS_W = 74;
    private static final int CENSUS_Y = 24;
    private static final int ROW_H = 10;
    private static final int CAP_MINUS_X = 186;
    private static final int CAP_PLUS_X = 232;

    public BreedingPodScreen(BreedingPodMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected String voiceKey() {
        return "breeding_pod";
    }

    @Override
    protected StatsPanel createStatsPanel() {
        return new StatsPanel(
                new StatsPanel.Row(StatsPanel.Icon.SPEED,
                        () -> Component.literal("×" + String.format("%.1f",
                                (2 + modulePoints(ModuleType.SPEED)) / 2.0)),
                        Component.translatable("gui.krios_industry.stats.animal_time")),
                new StatsPanel.Row(StatsPanel.Icon.ENERGY_MULT,
                        () -> Component.literal("×" + String.format("%.2f",
                                KoiosBalance.efficiencyPermille(modulePoints(ModuleType.EFFICIENCY)) / 1000.0)),
                        Component.translatable("gui.krios_industry.stats.energy")),
                new StatsPanel.Row(StatsPanel.Icon.YIELD,
                        () -> Component.literal("+" + modulePoints(ModuleType.YIELD) * 25 + " %"),
                        Component.translatable("gui.krios_industry.stats.bonus_babies")),
                new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                        () -> Component.literal(menu.drawPerTick() + " E/t"),
                        Component.translatable("gui.krios_industry.stats.consumption")),
                new StatsPanel.Row(StatsPanel.Icon.ZONE,
                        () -> Component.literal(Integer.toString(menu.totalAnimals())),
                        Component.translatable("gui.krios_industry.stats.animals")));
    }

    // --- Work area ---

    /** Rows to draw: species, the last one becoming "+n" when more exist. */
    private int speciesRows() {
        return menu.otherSpecies() > 0 ? BreedingPodBlockEntity.CENSUS_ROWS - 1 : BreedingPodBlockEntity.CENSUS_ROWS;
    }

    @Override
    protected void renderWork(GuiGraphics gui) {
        renderZoneControls(gui);
        int x = leftPos + CENSUS_X;
        int cap = menu.cap();
        boolean any = false;
        for (int row = 0; row < speciesRows(); row++) {
            EntityType<?> type = menu.species(row);
            if (type == null) {
                continue;
            }
            any = true;
            int y = topPos + CENSUS_Y + row * ROW_H;
            int count = menu.speciesCount(row);
            String value = count + "/" + cap;
            int valueW = font.width(value);
            String name = font.plainSubstrByWidth(type.getDescription().getString(), CENSUS_W - valueW - 4);
            gui.drawString(font, name, x, y, COLOR_TEXT_DIM, false);
            gui.drawString(font, value, x + CENSUS_W - valueW, y,
                    count >= cap ? 0xFF000000 | COLOR_ENERGY : 0xFFE8EAF0, false);
        }
        if (menu.otherSpecies() > 0) {
            int y = topPos + CENSUS_Y + speciesRows() * ROW_H;
            gui.drawString(font, Component.translatable("gui.krios_industry.pod.others",
                    menu.otherSpecies() + 1), x, y, COLOR_TEXT_DIM, false);
        }
        if (!any) {
            gui.drawString(font, Component.translatable("gui.krios_industry.pod.empty"),
                    x, topPos + CENSUS_Y, COLOR_TEXT_DIM, false);
        }
        // Cap selector, under the census.
        int y = topPos + ZoneMachineMenu.CONTROLS_Y;
        boolean canLower = cap > 1;
        boolean canRaise = cap < menu.capMax();
        int minusX = leftPos + CAP_MINUS_X;
        int plusX = leftPos + CAP_PLUS_X;
        drawButton(gui, minusX, y, BTN, canLower);
        int glyph = canLower ? 0xFFE8EAF0 : 0xFF5A6170;
        gui.fill(minusX + 2, y + 4, minusX + 8, y + 6, glyph);
        drawButton(gui, plusX, y, BTN, canRaise);
        glyph = canRaise ? 0xFFE8EAF0 : 0xFF5A6170;
        gui.fill(plusX + 2, y + 4, plusX + 8, y + 6, glyph);
        gui.fill(plusX + 4, y + 2, plusX + 6, y + 8, glyph);
        gui.drawCenteredString(font, Integer.toString(cap), leftPos + (CAP_MINUS_X + BTN + CAP_PLUS_X) / 2,
                y + 1, 0xFFE8EAF0);
    }

    private boolean capMinusHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, CAP_MINUS_X, ZoneMachineMenu.CONTROLS_Y, BTN, BTN);
    }

    private boolean capPlusHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, CAP_PLUS_X, ZoneMachineMenu.CONTROLS_Y, BTN, BTN);
    }

    private boolean capValueHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, CAP_MINUS_X + BTN, ZoneMachineMenu.CONTROLS_Y, CAP_PLUS_X - CAP_MINUS_X - BTN, BTN);
    }

    private int censusRowAt(double mouseX, double mouseY) {
        for (int row = 0; row < speciesRows(); row++) {
            if (menu.species(row) != null
                    && hit(mouseX, mouseY, CENSUS_X, CENSUS_Y + row * ROW_H - 1, CENSUS_W, ROW_H)) {
                return row;
            }
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        if (renderZoneTooltip(gui, mouseX, mouseY)) {
            return;
        }
        int row = censusRowAt(mouseX, mouseY);
        if (row >= 0) {
            gui.renderTooltip(font, Component.translatable("gui.krios_industry.pod.census_row",
                    menu.species(row).getDescription(), menu.speciesCount(row), menu.cap()), mouseX, mouseY);
        } else if (capMinusHit(mouseX, mouseY) || capPlusHit(mouseX, mouseY) || capValueHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.krios_industry.pod.cap", menu.cap(), menu.capMax()),
                    Component.translatable("gui.krios_industry.pod.cap_hint").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (clickZoneControls(mouseX, mouseY)) {
                return true;
            }
            boolean shift = Screen.hasShiftDown();
            if (capMinusHit(mouseX, mouseY) && menu.cap() > 1) {
                clickButton(shift ? BreedingPodBlockEntity.BUTTON_CAP_DOWN_8 : BreedingPodBlockEntity.BUTTON_CAP_DOWN);
                return true;
            }
            if (capPlusHit(mouseX, mouseY) && menu.cap() < menu.capMax()) {
                clickButton(shift ? BreedingPodBlockEntity.BUTTON_CAP_UP_8 : BreedingPodBlockEntity.BUTTON_CAP_UP);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- Voice ---

    @Override
    protected Component idlePhrase(int stateCode) {
        String base = "gui.krios_industry.voice.breeding_pod.";
        return switch (stateCode) {
            case BreedingPodBlockEntity.STATE_CAPPED -> Component.translatable(base + "capped");
            case BreedingPodBlockEntity.STATE_NO_FOOD -> Component.translatable(base + "no_food");
            case BreedingPodBlockEntity.STATE_NO_PAIR -> Component.translatable(base + "no_pair");
            case BreedingPodBlockEntity.STATE_NO_ANIMALS -> Component.translatable(base + "no_animals");
            default -> null;
        };
    }
}
