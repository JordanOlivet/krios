package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.machine.forge.PlasmaForgeBlockEntity;
import io.lakio.krios.industry.machine.forge.PlasmaForgeMenu;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class PlasmaForgeScreen extends MachineTerminalScreen<PlasmaForgeMenu> {


    private final HologramFacePanel facePanel = new HologramFacePanel(
            HologramFacePanel.columnsFor(PlasmaForgeBlockEntity.SUPPORTED_FACE_BITS));

    private final ModuleBayPanel moduleBay = new ModuleBayPanel(
            () -> menu.activeModuleSlots(), menu::setModuleBayOpen);

    // Module effects are visible container slots — no extra sync needed.
    // Points model: every row reads the per-axis point total.
    private final StatsPanel statsPanel = new StatsPanel(
            // Per-column pace: tiers add COLUMNS, modules add speed.
            new StatsPanel.Row(StatsPanel.Icon.SPEED,
                    () -> Component.literal("×" + String.format("%.1f",
                            (2 + modulePoints(ModuleType.SPEED)) / 2.0)),
                    Component.translatable("gui.krios_industry.stats.speed")),
            new StatsPanel.Row(StatsPanel.Icon.ENERGY_MULT,
                    () -> Component.literal("×" + String.format("%.2f",
                            io.lakio.krios.industry.koios.KoiosBalance.efficiencyPermille(
                                    modulePoints(ModuleType.EFFICIENCY)) / 1000.0)),
                    Component.translatable("gui.krios_industry.stats.energy")),
            new StatsPanel.Row(StatsPanel.Icon.YIELD,
                    () -> Component.literal("+" + modulePoints(ModuleType.YIELD) * 25 + " %"),
                    Component.translatable("gui.krios_industry.stats.yield")),
            new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                    () -> Component.literal(menu.drawPerTick() + " E/t"),
                    Component.translatable("gui.krios_industry.stats.consumption")));

    private final AnnexPanel[] annexes = {moduleBay, statsPanel, facePanel};

    public PlasmaForgeScreen(PlasmaForgeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private int modulePoints(ModuleType type) {
        int points = 0;
        for (int i = PlasmaForgeBlockEntity.MODULE_SLOT_FIRST; i <= PlasmaForgeBlockEntity.MODULE_SLOT_LAST; i++) {
            if (menu.getSlot(i).getItem().getItem() instanceof ModuleItem module
                    && module.getType() == type) {
                points += module.points();
            }
        }
        return points;
    }

    @Override
    protected AnnexPanel[] annexPanels() {
        return annexes;
    }

    @Override
    protected HologramFacePanel facePanel() {
        return facePanel;
    }

    @Override
    protected ModuleBayPanel moduleBay() {
        return moduleBay;
    }

    @Override
    protected boolean hasDistributionToggle() {
        return true;
    }

    @Override
    protected void renderWork(GuiGraphics guiGraphics) {
        // One vertical bar per active process column (parallel columns).
        for (int column = 0; column < menu.activeColumns(); column++) {
            drawColumnProgress(guiGraphics, leftPos + PlasmaForgeMenu.PROCESS_COL_X[column],
                    topPos + PlasmaForgeMenu.PROCESS_BAR_Y, PlasmaForgeMenu.PROCESS_BAR_H,
                    menu.getColumnProgress(column, PlasmaForgeMenu.PROCESS_BAR_H));
        }
    }

    @Override
    protected Component workingPhrase() {
        for (int column = 0; column < menu.activeColumns(); column++) {
            ItemStack input = menu.getSlot(column).getItem();
            if (menu.isColumnWorking(column) && !input.isEmpty()) {
                return Component.translatable("gui.krios_industry.voice.plasma_forge.working",
                        input.getHoverName());
            }
        }
        return null;
    }
}
