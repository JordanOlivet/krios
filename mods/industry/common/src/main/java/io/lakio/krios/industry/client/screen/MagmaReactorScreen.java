package io.lakio.krios.industry.client.screen;

import io.lakio.krios.core.client.screen.AnnexPanel;
import io.lakio.krios.core.client.screen.HologramFacePanel;
import io.lakio.krios.core.client.screen.StatsPanel;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.magma.MagmaReactorBlockEntity;
import io.lakio.krios.industry.machine.magma.MagmaReactorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

/**
 * Magma Reactor terminal: the reactor's wide energy gauge on top, the lava
 * tank as a second wide gauge under it, the bucket input and output either
 * side of the flame.
 */
public class MagmaReactorScreen extends MachineTerminalScreen<MagmaReactorMenu> {

    /** Same pixel flame as the Thermal Reactor, in lava colors. */
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

    private static final int GAUGE_X = 8;
    private static final int GAUGE_W = 240;
    private static final int ENERGY_Y = 28;
    private static final int LAVA_Y = 42;
    private static final int GAUGE_H = 8;

    private final HologramFacePanel facePanel = new HologramFacePanel(
            HologramFacePanel.columnsFor(MagmaReactorBlockEntity.SUPPORTED_FACE_BITS));

    private final ModuleBayPanel moduleBay = new ModuleBayPanel(
            () -> menu.activeModuleSlots(), menu::setModuleBayOpen);

    private final StatsPanel statsPanel = new StatsPanel(
            new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                    () -> Component.literal(outputPerTick() + " E/t"),
                    Component.translatable("gui.krios_industry.stats.output")),
            new StatsPanel.Row(StatsPanel.Icon.STORED,
                    () -> Component.literal(menu.getEnergyStored() + " / " + menu.getEnergyCapacity() + " E"),
                    Component.translatable("gui.krios_industry.stats.stored")),
            new StatsPanel.Row(StatsPanel.Icon.STORED,
                    () -> Component.literal(menu.getLavaStored() + " / " + menu.getLavaCapacity() + " mB"),
                    Component.translatable("gui.krios_industry.stats.lava")),
            new StatsPanel.Row(StatsPanel.Icon.ENERGY_MULT,
                    () -> energyBonus(menu.bayEnergyPoints(), menu.getEffectiveTier()),
                    Component.translatable("gui.krios_industry.stats.energy_bonus")));

    private final AnnexPanel[] annexes = {moduleBay, statsPanel, facePanel};

    public MagmaReactorScreen(MagmaReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** Live output: base x tier factor x counted Energy points, 0 while unlit. */
    private int outputPerTick() {
        if (!menu.isBurning()) {
            return 0;
        }
        int tier = menu.getEffectiveTier();
        int points = menu.networkLevel() > 0
                ? MachineTiers.countedEnergyPoints(menu.bayEnergyPoints(), tier) : 0;
        return MagmaReactorBlockEntity.GENERATION_PER_TICK * MachineTiers.speedFactor(tier) * (4 + points) / 4;
    }

    /**
     * Energy bonus (ADR-039), the Yield line's shape: the counted points'
     * bonus, then counted / the effective tier's cap, amber while modules
     * past the cap sit idle.
     */
    static Component energyBonus(int bayPoints, int effectiveTier) {
        int counted = MachineTiers.countedEnergyPoints(bayPoints, effectiveTier);
        MutableComponent value = Component.literal("+" + counted * 25 + " % · " + counted + "/"
                + MachineTiers.energyPointCap(effectiveTier));
        return bayPoints > counted ? value.withStyle(ChatFormatting.GOLD) : value;
    }

    @Override
    protected AnnexPanel[] annexPanels() {
        return annexes;
    }

    @Override
    protected ModuleBayPanel moduleBay() {
        return moduleBay;
    }

    @Override
    protected HologramFacePanel facePanel() {
        return facePanel;
    }

    @Override
    protected int energyBarX() {
        return GAUGE_X;
    }

    @Override
    protected int energyBarY() {
        return ENERGY_Y;
    }

    @Override
    protected int energyBarWidth() {
        return GAUGE_W;
    }

    @Override
    protected int energyBarHeight() {
        return GAUGE_H;
    }

    @Override
    protected boolean energyBarHorizontal() {
        return true;
    }

    @Override
    protected void renderWork(GuiGraphics gui) {
        // Lava tank: the energy gauge's twin, in lava orange.
        int x = leftPos + GAUGE_X;
        int y = topPos + LAVA_Y;
        gui.fill(x - 1, y - 1, x + GAUGE_W + 1, y + GAUGE_H + 1, 0xFF0C0E13);
        gui.fill(x, y, x + GAUGE_W, y + GAUGE_H, 0xFF12151B);
        int fill = menu.getLavaProgress(GAUGE_W);
        if (fill > 0) {
            gui.fill(x, y, x + fill, y + GAUGE_H, 0xFFE2501C);
            gui.fill(x + fill - 1, y, x + fill, y + GAUGE_H, 0xFFFFA040);
        }
        if (!menu.isBurning()) {
            return;
        }
        int burn = menu.getBurnProgress(FLAME.length);
        int left = leftPos + WORK_CENTER_X - FLAME[0].length() / 2 + 1;
        int bottom = topPos + MagmaReactorMenu.BUCKET_Y + 14;
        for (int row = FLAME.length - burn; row < FLAME.length; row++) {
            int rowY = bottom - (FLAME.length - 1 - row);
            String line = FLAME[row];
            for (int col = 0; col < line.length(); col++) {
                char pixel = line.charAt(col);
                if (pixel != '.') {
                    gui.fill(left + col, rowY, left + col + 1, rowY + 1,
                            pixel == '2' ? 0xFFFFD070 : 0xFFE2501C);
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        int x = leftPos + GAUGE_X;
        int y = topPos + LAVA_Y;
        if (mouseX >= x && mouseX < x + GAUGE_W && mouseY >= y && mouseY < y + GAUGE_H) {
            gui.renderTooltip(font, Component.translatable("gui.krios_industry.magma_reactor.tank",
                    menu.getLavaStored(), menu.getLavaCapacity()), mouseX, mouseY);
        }
    }

    @Override
    protected Component workingPhrase() {
        return menu.isBurning()
                ? Component.translatable("gui.krios_industry.voice.magma_reactor.working")
                : null;
    }

    @Override
    public java.util.Map<String, Object> readout() {
        java.util.Map<String, Object> out = super.readout();
        out.put("lava", menu.getLavaStored());
        out.put("lavaCapacity", menu.getLavaCapacity());
        out.put("burning", menu.isBurning());
        return out;
    }
}
