package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.machine.solar.SolarPanelBlockEntity;
import io.lakio.krios.industry.machine.solar.SolarPanelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SolarPanelScreen extends MachineTerminalScreen<SolarPanelMenu> {

    /**
     * Pixel sun in the work center, feed-style pixel map: 1 = corona and
     * rays, 2 = core. Its colors carry the production state (amber =
     * full sun, washed copper = rain, gunmetal = sky obstructed).
     */
    private static final String[] SUN = {
            "......1......",
            "..1...1...1..",
            "...1.....1...",
            "....11111....",
            "...1222221...",
            "...1222221...",
            "1..1222221..1",
            "...1222221...",
            "...1222221...",
            "....11111....",
            "...1.....1...",
            "..1...1...1..",
            "......1......",
    };

    private final HologramFacePanel facePanel = new HologramFacePanel(
            HologramFacePanel.columnsFor(SolarPanelBlockEntity.SUPPORTED_FACE_BITS));

    private final StatsPanel statsPanel = new StatsPanel(
            new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                    () -> Component.literal(menu.getRate() + " E/t"),
                    Component.translatable("gui.krios_industry.stats.output")),
            new StatsPanel.Row(StatsPanel.Icon.STORED,
                    () -> Component.literal(menu.getEnergyStored() + " / " + menu.getEnergyCapacity() + " E"),
                    Component.translatable("gui.krios_industry.stats.stored")));

    private final AnnexPanel[] annexes = {statsPanel, facePanel};

    public SolarPanelScreen(SolarPanelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected AnnexPanel[] annexPanels() {
        return annexes;
    }

    @Override
    protected HologramFacePanel facePanel() {
        return facePanel;
    }

    // Same showcase gauge as the generator: wide horizontal bar across
    // the top of the work area.
    @Override
    protected int energyBarX() {
        return 8;
    }

    @Override
    protected int energyBarY() {
        return 32;
    }

    @Override
    protected int energyBarWidth() {
        return 240;
    }

    @Override
    protected int energyBarHeight() {
        return 8;
    }

    @Override
    protected boolean energyBarHorizontal() {
        return true;
    }

    @Override
    protected void renderWork(GuiGraphics guiGraphics) {
        int state = menu.getProductionState();
        int core;
        int corona;
        switch (state) {
            case SolarPanelBlockEntity.STATE_CLEAR, SolarPanelBlockEntity.STATE_FULL -> {
                core = 0xFFFFD98F;
                corona = 0xFFFFB03A;
            }
            case SolarPanelBlockEntity.STATE_RAIN -> {
                core = 0xFFC9752E;
                corona = 0xFF7A431C;
            }
            case SolarPanelBlockEntity.STATE_COVERED -> {
                core = 0xFF3A414E;
                corona = 0xFF262B35;
            }
            default -> {
                core = 0;
                corona = 0;
            }
        }
        int sunTop = topPos + 44;
        if (core != 0) {
            int left = leftPos + WORK_CENTER_X - SUN[0].length() / 2;
            for (int row = 0; row < SUN.length; row++) {
                String line = SUN[row];
                for (int col = 0; col < line.length(); col++) {
                    char pixel = line.charAt(col);
                    if (pixel != '.') {
                        guiGraphics.fill(left + col, sunTop + row, left + col + 1, sunTop + row + 1,
                                pixel == '2' ? core : corona);
                    }
                }
            }
        }
        Component label = Component.translatable("gui.krios_industry.solar.state." + stateKey(state));
        int labelX = leftPos + WORK_CENTER_X - font.width(label) / 2;
        guiGraphics.drawString(font, label, labelX, sunTop + SUN.length + 6, 0xFF9AA3B2, false);
    }

    private static String stateKey(int state) {
        return switch (state) {
            case SolarPanelBlockEntity.STATE_CLEAR -> "clear";
            case SolarPanelBlockEntity.STATE_RAIN -> "rain";
            case SolarPanelBlockEntity.STATE_FULL -> "full";
            case SolarPanelBlockEntity.STATE_COVERED -> "covered";
            default -> "night";
        };
    }

    @Override
    protected Component workingPhrase() {
        return switch (menu.getProductionState()) {
            case SolarPanelBlockEntity.STATE_CLEAR ->
                    Component.translatable("gui.krios_industry.voice.solar.clear");
            case SolarPanelBlockEntity.STATE_RAIN ->
                    Component.translatable("gui.krios_industry.voice.solar.rain");
            case SolarPanelBlockEntity.STATE_FULL ->
                    Component.translatable("gui.krios_industry.voice.solar.full");
            case SolarPanelBlockEntity.STATE_COVERED ->
                    Component.translatable("gui.krios_industry.voice.solar.covered");
            default -> null;  // night: the idle/offline rotation takes over
        };
    }
}
