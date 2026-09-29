package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.machine.generator.GeneratorBlockEntity;
import io.lakio.krios.industry.machine.generator.GeneratorMenu;
import io.lakio.krios.industry.network.Flow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class GeneratorScreen extends MachineTerminalScreen<GeneratorMenu> {

    private final HologramFacePanel facePanel = new HologramFacePanel(
            new HologramFacePanel.Column(Flow.ENERGY, true),
            new HologramFacePanel.Column(Flow.ITEM, false));

    private final StatsPanel statsPanel = new StatsPanel(
            new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                    () -> Component.literal((menu.isBurning()
                            ? GeneratorBlockEntity.GENERATION_PER_TICK : 0) + " E/t"),
                    Component.translatable("gui.krios_industry.stats.output")),
            new StatsPanel.Row(StatsPanel.Icon.STORED,
                    () -> Component.literal(menu.getEnergyStored() + " / " + menu.getEnergyCapacity() + " E"),
                    Component.translatable("gui.krios_industry.stats.stored")));

    private final AnnexPanel[] annexes = {statsPanel, facePanel};

    public GeneratorScreen(GeneratorMenu menu, Inventory playerInventory, Component title) {
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

    @Override
    protected void renderWork(GuiGraphics guiGraphics) {
        // Pixel flame above the fuel slot, height following the burn.
        if (!menu.isBurning()) {
            return;
        }
        int burn = menu.getBurnProgress(12);
        int baseX = leftPos + WORK_CENTER_X;
        int baseY = topPos + 42;
        for (int i = 0; i < burn; i++) {
            int half = Math.max(1, (12 - i) / 2);
            int color = i < 4 ? 0xFFFFB03A : i < 8 ? 0xFFFFC964 : 0xFFFFE0A0;
            guiGraphics.fill(baseX - half, baseY - i, baseX + half, baseY - i + 1, color);
        }
    }

    @Override
    protected Component workingPhrase() {
        if (!menu.isBurning()) {
            return null;
        }
        ItemStack fuel = menu.getSlot(GeneratorMenu.FUEL_SLOT).getItem();
        if (fuel.isEmpty()) {
            return Component.translatable("gui.krios_industry.voice.generator.working_generic");
        }
        return Component.translatable("gui.krios_industry.voice.generator.working",
                fuel.getHoverName());
    }
}
