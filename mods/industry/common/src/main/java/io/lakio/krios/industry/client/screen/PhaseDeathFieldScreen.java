package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.field.PhaseDeathFieldBlockEntity;
import io.lakio.krios.industry.machine.field.PhaseDeathFieldMenu;
import io.lakio.krios.industry.machine.module.ModuleType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase Death Field terminal (design 2026-10-13): no inputs, so the left
 * column hosts the target filter, the spare-breeders toggle and its N;
 * the shared zone map in the center, the 3x3 drop buffer on the right;
 * the Census annex shows targeted/total per species.
 */
public class PhaseDeathFieldScreen extends ZoneMachineScreen<PhaseDeathFieldMenu> {

    private static final int COL_X = 22;
    private static final int COL_W = 56;
    private static final int FILTER_LABEL_Y = 24;
    private static final int FILTER_Y = 34;
    private static final int SPARE_Y = 52;
    private static final int SPARE_N_Y = 66;
    private static final int N_MINUS_X = COL_X + 14;
    private static final int N_PLUS_X = COL_X + COL_W - BTN;

    private static final String[] FILTER_KEYS = {"passive", "hostile", "all"};

    public PhaseDeathFieldScreen(PhaseDeathFieldMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected String voiceKey() {
        return "phase_death_field";
    }

    @Override
    protected StatsPanel createStatsPanel() {
        return new StatsPanel(
                new StatsPanel.Row(StatsPanel.Icon.SPEED,
                        () -> Component.literal("×" + String.format("%.1f",
                                (2 + modulePoints(ModuleType.SPEED)) / 2.0)),
                        Component.translatable("gui.krios_industry.stats.hit_pace")),
                new StatsPanel.Row(StatsPanel.Icon.ENERGY_MULT,
                        () -> Component.literal("×" + String.format("%.2f",
                                KoiosBalance.efficiencyPermille(modulePoints(ModuleType.EFFICIENCY)) / 1000.0)),
                        Component.translatable("gui.krios_industry.stats.energy")),
                new StatsPanel.Row(StatsPanel.Icon.YIELD,
                        () -> Component.literal(Integer.toString(modulePoints(ModuleType.YIELD))),
                        Component.translatable("gui.krios_industry.stats.looting")),
                new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                        () -> Component.literal(menu.drawPerTick() + " E/t"),
                        Component.translatable("gui.krios_industry.stats.consumption")),
                new StatsPanel.Row(StatsPanel.Icon.ZONE,
                        () -> Component.literal(Integer.toString(
                                PhaseDeathFieldBlockEntity.damageForTier(menu.getEffectiveTier()))),
                        Component.translatable("gui.krios_industry.stats.damage")));
    }

    @Override
    protected AnnexPanel[] createExtraAnnexes() {
        return new AnnexPanel[] {new CensusPanel(new CensusPanel.Source() {
            @Override
            public CensusPanel.Row row(int index) {
                EntityType<?> type = menu.species(index);
                if (type == null) {
                    return null;
                }
                int count = menu.speciesCount(index);
                int targets = menu.censusDetail(index);
                return new CensusPanel.Row(type.getDescription(), targets + "/" + count,
                        targets > 0 ? 0xFFE8EAF0 : 0xFF000000 | COLOR_TEXT_DIM, List.of(),
                        List.of(type.getDescription(),
                                Component.translatable("gui.krios_industry.field.census_row", targets, count)
                                        .withStyle(ChatFormatting.GRAY)));
            }

            @Override
            public int speciesTotal() {
                return menu.speciesTotal();
            }
        })};
    }

    // --- Work area ---

    private Component filterLabel() {
        return Component.translatable("gui.krios_industry.field.filter." + FILTER_KEYS[menu.filter() % 3]);
    }

    @Override
    protected void renderWork(GuiGraphics gui) {
        renderZoneControls(gui);
        int x = leftPos + COL_X;
        gui.drawString(font, Component.translatable("gui.krios_industry.field.targets"),
                x, topPos + FILTER_LABEL_Y, COLOR_TEXT_DIM, false);
        // Filter: one wide button cycling passive / hostile / all.
        drawButton(gui, x, topPos + FILTER_Y, COL_W, true);
        int filterColor = menu.filter() == PhaseDeathFieldBlockEntity.FILTER_PASSIVE ? 0xFF5FE09A
                : menu.filter() == PhaseDeathFieldBlockEntity.FILTER_HOSTILE ? 0xFF000000 | COLOR_ERROR
                : 0xFF000000 | COLOR_ENERGY;
        gui.drawCenteredString(font, filterLabel(), x + COL_W / 2, topPos + FILTER_Y + 1, filterColor);
        // Spare toggle: a check box and its label.
        boolean spare = menu.spareOn();
        int y = topPos + SPARE_Y;
        drawButton(gui, x, y, BTN, true);
        if (spare) {
            gui.fill(x + 2, y + 2, x + BTN - 2, y + BTN - 2, 0xFF5FE09A);
        }
        gui.drawString(font, Component.translatable("gui.krios_industry.field.spare"), x + BTN + 4, y + 1,
                spare ? 0xFFE8EAF0 : COLOR_TEXT_DIM, false);
        // N selector, live only while sparing.
        y = topPos + SPARE_N_Y;
        int n = menu.spareCount();
        boolean canLower = spare && n > 1;
        boolean canRaise = spare && n < PhaseDeathFieldBlockEntity.SPARE_MAX;
        int minusX = leftPos + N_MINUS_X;
        int plusX = leftPos + N_PLUS_X;
        drawButton(gui, minusX, y, BTN, canLower);
        int glyph = canLower ? 0xFFE8EAF0 : 0xFF5A6170;
        gui.fill(minusX + 2, y + 4, minusX + 8, y + 6, glyph);
        drawButton(gui, plusX, y, BTN, canRaise);
        glyph = canRaise ? 0xFFE8EAF0 : 0xFF5A6170;
        gui.fill(plusX + 2, y + 4, plusX + 8, y + 6, glyph);
        gui.fill(plusX + 4, y + 2, plusX + 6, y + 8, glyph);
        gui.drawCenteredString(font, Integer.toString(n), leftPos + (N_MINUS_X + BTN + N_PLUS_X) / 2, y + 1,
                spare ? 0xFFE8EAF0 : 0xFF000000 | COLOR_TEXT_DIM);
    }

    private boolean filterHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, COL_X, FILTER_Y, COL_W, BTN);
    }

    private boolean spareHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, COL_X, SPARE_Y, COL_W, BTN);
    }

    private boolean nMinusHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, N_MINUS_X, SPARE_N_Y, BTN, BTN);
    }

    private boolean nPlusHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, N_PLUS_X, SPARE_N_Y, BTN, BTN);
    }

    private boolean nValueHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, N_MINUS_X + BTN, SPARE_N_Y, N_PLUS_X - N_MINUS_X - BTN, BTN);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        if (renderZoneTooltip(gui, mouseX, mouseY)) {
            return;
        }
        if (filterHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.krios_industry.field.filter", filterLabel()),
                    Component.translatable("gui.krios_industry.field.filter_hint").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        } else if (spareHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable(menu.spareOn() ? "gui.krios_industry.field.spare_on"
                            : "gui.krios_industry.field.spare_off", menu.spareCount()),
                    Component.translatable("gui.krios_industry.field.spare_hint").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        } else if (nMinusHit(mouseX, mouseY) || nPlusHit(mouseX, mouseY) || nValueHit(mouseX, mouseY)) {
            gui.renderTooltip(font, Component.translatable("gui.krios_industry.field.spare_n",
                    menu.spareCount()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (clickZoneControls(mouseX, mouseY)) {
                return true;
            }
            if (filterHit(mouseX, mouseY)) {
                clickButton(PhaseDeathFieldBlockEntity.BUTTON_FILTER);
                return true;
            }
            if (spareHit(mouseX, mouseY)) {
                clickButton(PhaseDeathFieldBlockEntity.BUTTON_SPARE_TOGGLE);
                return true;
            }
            if (menu.spareOn() && nMinusHit(mouseX, mouseY) && menu.spareCount() > 1) {
                clickButton(PhaseDeathFieldBlockEntity.BUTTON_SPARE_DOWN);
                return true;
            }
            if (menu.spareOn() && nPlusHit(mouseX, mouseY)
                    && menu.spareCount() < PhaseDeathFieldBlockEntity.SPARE_MAX) {
                clickButton(PhaseDeathFieldBlockEntity.BUTTON_SPARE_UP);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- Voice ---

    @Override
    protected Component idlePhrase(int stateCode) {
        return stateCode == PhaseDeathFieldBlockEntity.STATE_NO_TARGETS
                ? Component.translatable("gui.krios_industry.voice.phase_death_field.no_targets") : null;
    }
}
