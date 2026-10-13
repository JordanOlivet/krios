package io.lakio.krios.industry.client.screen;

import java.util.ArrayList;
import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.pod.HarvestPodBlockEntity;
import io.lakio.krios.industry.machine.pod.HarvestPodMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;

/**
 * Harvest Pod terminal (design 2026-10-13): container column, the shared
 * zone map and controls, 3x3 buffer; the census of what is harvestable
 * lives in the shared Census annex (ready/total per producing species,
 * shears/bucket/bowl/clock marks, red when the input lacks the container).
 */
public class HarvestPodScreen extends ZoneMachineScreen<HarvestPodMenu> {

    private static final String CENSUS = "gui.krios_industry.harvest_pod.census.";

    public HarvestPodScreen(HarvestPodMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected String voiceKey() {
        return "harvest_pod";
    }

    @Override
    protected StatsPanel createStatsPanel() {
        return new StatsPanel(
                new StatsPanel.Row(StatsPanel.Icon.SPEED,
                        () -> Component.literal("×" + String.format("%.1f",
                                (2 + modulePoints(ModuleType.SPEED)) / 2.0)),
                        Component.translatable("gui.krios_industry.stats.producer_time")),
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
                        () -> Component.literal(Integer.toString(menu.totalAnimals())),
                        Component.translatable("gui.krios_industry.stats.animals")));
    }

    @Override
    protected AnnexPanel[] createExtraAnnexes() {
        return new AnnexPanel[] {new CensusPanel(new CensusPanel.Source() {
            @Override
            public CensusPanel.Row row(int index) {
                EntityType<?> type = menu.species(index);
                return type == null ? null : censusRow(type, menu.speciesCount(index), menu.censusDetail(index));
            }

            @Override
            public int speciesTotal() {
                return menu.speciesTotal();
            }
        })};
    }

    private CensusPanel.Row censusRow(EntityType<?> type, int count, int detail) {
        int ready = detail & HarvestPodBlockEntity.DETAIL_READY_MASK;
        boolean producer = (detail & ~HarvestPodBlockEntity.DETAIL_READY_MASK) != 0;
        List<CensusPanel.Mark> marks = new ArrayList<>();
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(type.getDescription());
        if ((detail & HarvestPodBlockEntity.FLAG_SHEAR) != 0) {
            marks.add(new CensusPanel.Mark(CensusPanel.Glyph.SHEARS, false));
            tooltip.add(Component.translatable(CENSUS + "shear", ready, count).withStyle(ChatFormatting.GRAY));
        }
        if ((detail & HarvestPodBlockEntity.FLAG_STEW) != 0) {
            boolean missing = !menu.hasInput(Items.BOWL);
            marks.add(new CensusPanel.Mark(CensusPanel.Glyph.BOWL, missing));
            tooltip.add(Component.translatable(CENSUS + "stew").withStyle(ChatFormatting.GRAY));
            if (missing) {
                tooltip.add(Component.translatable(CENSUS + "no_bowl").withStyle(ChatFormatting.RED));
            }
        }
        if ((detail & HarvestPodBlockEntity.FLAG_MILK) != 0) {
            boolean missing = !menu.hasInput(Items.BUCKET);
            marks.add(new CensusPanel.Mark(CensusPanel.Glyph.BUCKET, missing));
            tooltip.add(Component.translatable(CENSUS + "milk", ready).withStyle(ChatFormatting.GRAY));
            if (missing) {
                tooltip.add(Component.translatable(CENSUS + "no_bucket").withStyle(ChatFormatting.RED));
            }
        }
        if ((detail & HarvestPodBlockEntity.FLAG_TIMED) != 0) {
            marks.add(new CensusPanel.Mark(CensusPanel.Glyph.TIMER, false));
            tooltip.add(Component.translatable(CENSUS + "timed", ready).withStyle(ChatFormatting.GRAY));
        }
        if (!producer) {
            tooltip.add(Component.translatable(CENSUS + "none", count).withStyle(ChatFormatting.GRAY));
        }
        String value = producer ? ready + "/" + count : Integer.toString(count);
        int color = producer && ready > 0 ? 0xFFE8EAF0 : 0xFF000000 | COLOR_TEXT_DIM;
        return new CensusPanel.Row(type.getDescription(), value, color, marks, tooltip);
    }

    // --- Work area ---

    @Override
    protected void renderWork(GuiGraphics gui) {
        renderZoneControls(gui);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        renderZoneTooltip(gui, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && clickZoneControls(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- Voice ---

    @Override
    protected Component idlePhrase(int stateCode) {
        String base = "gui.krios_industry.voice.harvest_pod.";
        return switch (stateCode) {
            case HarvestPodBlockEntity.STATE_NOTHING_READY -> Component.translatable(base + "nothing_ready");
            case HarvestPodBlockEntity.STATE_NO_CONTAINERS -> Component.translatable(base + "no_containers");
            case HarvestPodBlockEntity.STATE_NO_ANIMALS -> Component.translatable(base + "no_animals");
            default -> null;
        };
    }
}
