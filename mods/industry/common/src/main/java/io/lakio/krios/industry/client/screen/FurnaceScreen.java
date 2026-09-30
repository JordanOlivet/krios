package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.machine.furnace.FurnaceBlockEntity;
import io.lakio.krios.industry.machine.furnace.FurnaceMenu;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class FurnaceScreen extends MachineTerminalScreen<FurnaceMenu> {


    private final HologramFacePanel facePanel = new HologramFacePanel(
            HologramFacePanel.columnsFor(FurnaceBlockEntity.SUPPORTED_FACE_BITS));

    private final ModuleBayPanel moduleBay = new ModuleBayPanel(
            FurnaceBlockEntity.MODULE_SLOT_COUNT, menu::setModuleBayOpen);

    // Module effects are visible container slots — no extra sync needed.
    private final StatsPanel statsPanel = new StatsPanel(
            // Per-column pace: tiers add COLUMNS, modules add speed.
            new StatsPanel.Row(StatsPanel.Icon.SPEED,
                    () -> Component.literal("×" + String.format("%.1f",
                            (2 + moduleCount(ModuleType.SPEED)) / 2.0)),
                    Component.translatable("gui.krios_industry.stats.speed")),
            new StatsPanel.Row(StatsPanel.Icon.ENERGY_MULT,
                    () -> Component.literal("×" + new String[]{"1.0", "0.8", "0.64"}
                            [moduleCount(ModuleType.EFFICIENCY)]),
                    Component.translatable("gui.krios_industry.stats.energy")),
            new StatsPanel.Row(StatsPanel.Icon.YIELD,
                    () -> Component.literal("+" + moduleCount(ModuleType.YIELD) * 25 + " %"),
                    Component.translatable("gui.krios_industry.stats.yield")),
            new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                    () -> Component.literal(menu.drawPerTick() + " E/t"),
                    Component.translatable("gui.krios_industry.stats.consumption")));

    private final AnnexPanel[] annexes = {statsPanel, moduleBay, facePanel};

    public FurnaceScreen(FurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private int moduleCount(ModuleType type) {
        int count = 0;
        for (int i = FurnaceBlockEntity.MODULE_SLOT_FIRST; i <= FurnaceBlockEntity.MODULE_SLOT_LAST; i++) {
            if (menu.getSlot(i).getItem().getItem() instanceof ModuleItem module
                    && module.getType() == type) {
                count++;
            }
        }
        return count;
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
            drawColumnProgress(guiGraphics, leftPos + FurnaceMenu.PROCESS_COL_X[column],
                    topPos + FurnaceMenu.PROCESS_BAR_Y, FurnaceMenu.PROCESS_BAR_H,
                    menu.getColumnProgress(column, FurnaceMenu.PROCESS_BAR_H));
        }
    }

    @Override
    protected Component workingPhrase() {
        for (int column = 0; column < menu.activeColumns(); column++) {
            ItemStack input = menu.getSlot(column).getItem();
            if (menu.isColumnWorking(column) && !input.isEmpty()) {
                return Component.translatable("gui.krios_industry.voice.furnace.working",
                        input.getHoverName());
            }
        }
        return null;
    }
}
