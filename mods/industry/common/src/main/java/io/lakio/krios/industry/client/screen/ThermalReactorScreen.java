package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.machine.reactor.ThermalReactorBlockEntity;
import io.lakio.krios.industry.machine.reactor.ThermalReactorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ThermalReactorScreen extends MachineTerminalScreen<ThermalReactorMenu> {

    /**
     * Pixel flame above the fuel slot, feed-style pixel map: 1 = amber
     * body, 2 = bright core. Rows disappear from the top as the burn runs
     * out (vanilla furnace read).
     */
    private static final String[] FLAME = {
            ".....1...",
            "....11...",
            "....11.1.",
            "...111.1.",
            "...1211..",
            "..11211..",
            "..122211.",
            ".1122211.",
            ".1222211.",
            ".1122211.",
            ".112211..",
            "..11111..",
    };

    private final HologramFacePanel facePanel = new HologramFacePanel(
            HologramFacePanel.columnsFor(ThermalReactorBlockEntity.SUPPORTED_FACE_BITS));

    private final ModuleBayPanel moduleBay = new ModuleBayPanel(
            () -> menu.activeModuleSlots(), menu::setModuleBayOpen);

    private final StatsPanel statsPanel = new StatsPanel(
            new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                    () -> Component.literal((menu.isBurning()
                            ? ThermalReactorBlockEntity.GENERATION_PER_TICK
                                    * io.lakio.krios.industry.machine.MachineTiers
                                            .speedFactor(menu.getEffectiveTier())
                                    * (4 + energyPoints()) / 4
                            : 0) + " E/t"),
                    Component.translatable("gui.krios_industry.stats.output")),
            new StatsPanel.Row(StatsPanel.Icon.STORED,
                    () -> Component.literal(menu.getEnergyStored() + " / " + menu.getEnergyCapacity() + " E"),
                    Component.translatable("gui.krios_industry.stats.stored")));

    private final AnnexPanel[] annexes = {statsPanel, facePanel};
    private final AnnexPanel[] annexesWithBay = {moduleBay, statsPanel, facePanel};

    public ThermalReactorScreen(ThermalReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** Energy-module point total (points model, 2026-10-01). */
    private int energyPoints() {
        int points = 0;
        for (int i = ThermalReactorBlockEntity.MODULE_SLOT_FIRST; i <= ThermalReactorBlockEntity.MODULE_SLOT_LAST; i++) {
            if (menu.getSlot(i).getItem().getItem()
                            instanceof io.lakio.krios.industry.machine.module.ModuleItem module
                    && module.getType() == io.lakio.krios.industry.machine.module.ModuleType.ENERGY) {
                points += module.points();
            }
        }
        return points;
    }

    @Override
    protected AnnexPanel[] annexPanels() {
        // The bay only exists at tier 2+ (t1 stays deliberately boring).
        return menu.activeModuleSlots() > 0 ? annexesWithBay : annexes;
    }

    @Override
    protected ModuleBayPanel moduleBay() {
        return menu.activeModuleSlots() > 0 ? moduleBay : null;
    }

    @Override
    protected HologramFacePanel facePanel() {
        return facePanel;
    }

    // The reactor's gauge is its showcase: wide horizontal bar across
    // the top of the work area (agreed layout), not the left-rail sliver.
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
        if (!menu.isBurning()) {
            return;
        }
        int burn = menu.getBurnProgress(FLAME.length);
        // Bottom row sits just above the fuel slot; the flame keeps its
        // base and loses rows from the tip as the fuel runs out.
        int left = leftPos + WORK_CENTER_X - FLAME[0].length() / 2;
        int bottom = topPos + 56;
        for (int row = FLAME.length - burn; row < FLAME.length; row++) {
            int y = bottom - (FLAME.length - 1 - row);
            String line = FLAME[row];
            for (int col = 0; col < line.length(); col++) {
                char pixel = line.charAt(col);
                if (pixel != '.') {
                    guiGraphics.fill(left + col, y, left + col + 1, y + 1,
                            pixel == '2' ? 0xFFFFE0A0 : 0xFFFFB03A);
                }
            }
        }
    }

    @Override
    protected Component workingPhrase() {
        if (!menu.isBurning()) {
            return null;
        }
        ItemStack fuel = menu.getSlot(ThermalReactorMenu.FUEL_SLOT).getItem();
        if (fuel.isEmpty()) {
            return Component.translatable("gui.krios_industry.voice.thermal_reactor.working_generic");
        }
        return Component.translatable("gui.krios_industry.voice.thermal_reactor.working",
                fuel.getHoverName());
    }
}
