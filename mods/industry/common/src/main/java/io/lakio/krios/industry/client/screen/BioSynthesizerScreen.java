package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.synth.BioSynthesizerBlockEntity;
import io.lakio.krios.industry.machine.synth.BioSynthesizerMenu;
import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;

/**
 * Bio Synthesizer terminal (design 2026-10-13): the Engram slot in the
 * left column with its species (or "biome mode") under it, the shared zone
 * map and controls, and where the buffer would be, the zone's mob count
 * against the cap; the Census annex lists what stands in the zone.
 */
public class BioSynthesizerScreen extends ZoneMachineScreen<BioSynthesizerMenu> {

    private static final int MODE_X = 18;
    private static final int MODE_W = 66;
    private static final int MODE_Y = ZoneMachineMenu.SLOT_TOP_Y + 22;
    private static final int CAP_X = 172;
    private static final int CAP_W = 74;
    private static final int CAP_Y = 26;

    public BioSynthesizerScreen(BioSynthesizerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected String voiceKey() {
        return "bio_synthesizer";
    }

    @Override
    protected StatsPanel createStatsPanel() {
        return new StatsPanel(
                new StatsPanel.Row(StatsPanel.Icon.SPEED,
                        () -> Component.literal("×" + String.format("%.1f",
                                (2 + modulePoints(ModuleType.SPEED)) / 2.0)),
                        Component.translatable("gui.krios_industry.stats.cycle_pace")),
                new StatsPanel.Row(StatsPanel.Icon.ENERGY_MULT,
                        () -> Component.literal("×" + String.format("%.2f",
                                KoiosBalance.efficiencyPermille(modulePoints(ModuleType.EFFICIENCY)) / 1000.0)),
                        Component.translatable("gui.krios_industry.stats.energy")),
                new StatsPanel.Row(StatsPanel.Icon.YIELD,
                        () -> Component.literal("+" + modulePoints(ModuleType.YIELD) * 25 + " %"),
                        Component.translatable("gui.krios_industry.stats.bonus_mobs")),
                new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                        () -> Component.literal(menu.drawPerTick() + " E/t"),
                        Component.translatable("gui.krios_industry.stats.consumption")),
                new StatsPanel.Row(StatsPanel.Icon.ZONE,
                        () -> Component.literal(Integer.toString(menu.totalAnimals())),
                        Component.translatable("gui.krios_industry.stats.mobs")));
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
                return new CensusPanel.Row(type.getDescription(), Integer.toString(count), 0xFFE8EAF0,
                        List.of(), List.of(type.getDescription()));
            }

            @Override
            public int speciesTotal() {
                return menu.speciesTotal();
            }
        })};
    }

    // --- Work area ---

    private Component modeLine() {
        EntityType<?> species = menu.engramSpecies();
        return species == null ? Component.translatable("gui.krios_industry.synth.biome_mode")
                : species.getDescription();
    }

    @Override
    protected void renderWork(GuiGraphics gui) {
        renderZoneControls(gui);
        // Mode under the Engram slot.
        boolean engram = menu.engramSpecies() != null;
        String mode = font.plainSubstrByWidth(modeLine().getString(), MODE_W);
        gui.drawCenteredString(font, mode, leftPos + MODE_X + MODE_W / 2, topPos + MODE_Y,
                engram ? 0xFF5FE09A : 0xFF000000 | COLOR_TEXT_DIM);
        // Mobs in the zone against the cap.
        int x = leftPos + CAP_X;
        int y = topPos + CAP_Y;
        gui.drawString(font, Component.translatable("gui.krios_industry.synth.mobs"), x, y, COLOR_TEXT_DIM, false);
        int count = menu.totalAnimals();
        int cap = menu.cap();
        gui.drawString(font, count + " / " + cap, x, y + 12,
                count >= cap ? 0xFF000000 | COLOR_ENERGY : 0xFFE8EAF0, false);
        // Fill gauge.
        int gaugeY = y + 26;
        gui.fill(x, gaugeY, x + CAP_W, gaugeY + 4, 0xFF1B202A);
        int fill = Math.min(CAP_W, CAP_W * count / cap);
        gui.fill(x, gaugeY, x + fill, gaugeY + 4, count >= cap ? 0xFF000000 | COLOR_ENERGY : 0xFF5FE09A);
    }

    private boolean modeHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, MODE_X, MODE_Y - 1, MODE_W, 10);
    }

    private boolean capHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, CAP_X, CAP_Y, CAP_W, 32);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        if (renderZoneTooltip(gui, mouseX, mouseY)) {
            return;
        }
        if (modeHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    menu.engramSpecies() == null
                            ? Component.translatable("gui.krios_industry.synth.biome_mode")
                            : Component.translatable("gui.krios_industry.synth.engram_mode", modeLine()),
                    Component.translatable(menu.engramSpecies() == null ? "gui.krios_industry.synth.biome_hint"
                            : "gui.krios_industry.synth.engram_hint").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        } else if (capHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.krios_industry.synth.cap", menu.totalAnimals(), menu.cap()),
                    Component.translatable("gui.krios_industry.synth.cap_hint").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        }
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
        String base = "gui.krios_industry.voice.bio_synthesizer.";
        return switch (stateCode) {
            case BioSynthesizerBlockEntity.STATE_CAPPED -> Component.translatable(base + "capped");
            case BioSynthesizerBlockEntity.STATE_NO_ROOM -> Component.translatable(base + "no_room");
            case BioSynthesizerBlockEntity.STATE_NOTHING_SPAWNS -> Component.translatable(base + "nothing_spawns");
            case BioSynthesizerBlockEntity.STATE_PEACEFUL -> Component.translatable(base + "peaceful");
            default -> null;
        };
    }
}
