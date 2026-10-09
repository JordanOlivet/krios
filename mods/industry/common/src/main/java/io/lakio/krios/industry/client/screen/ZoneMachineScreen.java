package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.client.ZoneHologramState;
import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Shared zone-machine terminal (ADR-025): annexes (module bay, the
 * machine's stats, faces), the in-world hologram eye toggle, the shared
 * voice states, and drawing helpers. Work areas belong to the machines
 * (the planting map and controls, the Lure's line gauges).
 */
public abstract class ZoneMachineScreen<T extends ZoneMachineMenu> extends MachineTerminalScreen<T> {

    protected static final int BTN = 10;
    protected static final int EYE_W = 12;

    private final HologramFacePanel facePanel = new HologramFacePanel(
            HologramFacePanel.columnsFor(ZoneMachineBlockEntity.SUPPORTED_FACE_BITS));

    private final ModuleBayPanel moduleBay = new ModuleBayPanel(
            () -> menu.activeModuleSlots(), menu::setModuleBayOpen);

    private AnnexPanel[] annexes;

    protected ZoneMachineScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** The machine's stats annex (built once, after construction). */
    protected abstract StatsPanel createStatsPanel();

    /** Lang segment of this machine's voice lines (voice.{key}.*). */
    protected abstract String voiceKey();

    /** Voice line for the machine's own idle states; null when idle. */
    protected abstract Component idlePhrase(int stateCode);

    protected int modulePoints(ModuleType type) {
        int points = 0;
        int first = menu.moduleSlotFirst();
        for (int i = first; i < first + ZoneMachineBlockEntity.MODULE_SLOT_COUNT; i++) {
            if (menu.getSlot(i).getItem().getItem() instanceof ModuleItem module
                    && module.getType() == type) {
                points += module.points();
            }
        }
        return points;
    }

    @Override
    protected AnnexPanel[] annexPanels() {
        if (annexes == null) {
            annexes = new AnnexPanel[] {moduleBay, createStatsPanel(), facePanel};
        }
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

    // --- Drawing helpers ---

    protected static void drawButton(GuiGraphics gui, int x, int y, int w, boolean enabled) {
        gui.fill(x, y, x + w, y + BTN, 0xFF0C0E13);
        gui.fill(x + 1, y + 1, x + w - 1, y + BTN - 1, enabled ? 0xFF262B35 : 0xFF1B202A);
    }

    /** Eye glyph: lit cyan while the hologram is pinned. */
    protected static void drawEye(GuiGraphics gui, int x, int y, boolean pinned) {
        drawButton(gui, x, y, EYE_W, true);
        int c = pinned ? 0xFF31D8FF : 0xFF5A6170;
        gui.fill(x + 4, y + 2, x + 8, y + 3, c);
        gui.fill(x + 2, y + 3, x + 4, y + 4, c);
        gui.fill(x + 8, y + 3, x + 10, y + 4, c);
        gui.fill(x + 2, y + 6, x + 4, y + 7, c);
        gui.fill(x + 8, y + 6, x + 10, y + 7, c);
        gui.fill(x + 4, y + 7, x + 8, y + 8, c);
        gui.fill(x + 1, y + 4, x + 2, y + 6, c);
        gui.fill(x + 10, y + 4, x + 11, y + 6, c);
        gui.fill(x + 5, y + 4, x + 7, y + 6, pinned ? 0xFF9FF0FF : c);
    }

    /** Amber warning sign: a triangle with an exclamation mark. */
    protected static void drawWarning(GuiGraphics gui, int x, int y) {
        int amber = 0xFF000000 | COLOR_ENERGY;
        for (int row = 0; row < 9; row++) {
            int half = row / 2;
            gui.fill(x + 5 - half, y + 1 + row, x + 6 + half, y + 2 + row, amber);
        }
        gui.fill(x + 5, y + 3, x + 6, y + 7, 0xFF10141B);
        gui.fill(x + 5, y + 8, x + 6, y + 9, 0xFF10141B);
    }

    protected boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + w
                && mouseY >= topPos + y && mouseY < topPos + y + h;
    }

    // --- Hologram eye (each machine places it) ---

    protected void renderEye(GuiGraphics gui, int x, int y) {
        drawEye(gui, leftPos + x, topPos + y, ZoneHologramState.isPinned(menu.machinePos()));
    }

    protected void renderEyeTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        boolean pinned = ZoneHologramState.isPinned(menu.machinePos());
        gui.renderComponentTooltip(font, List.of(
                Component.translatable(pinned ? "gui.krios_industry.zone.hologram_pinned"
                        : "gui.krios_industry.zone.hologram_show"),
                Component.translatable("gui.krios_industry.zone.hologram_calibrator")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
    }

    protected void toggleEye() {
        ZoneHologramState.toggle(menu.machinePos());
    }

    protected void clickButton(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    // --- Voice ---

    @Override
    protected Component workingPhrase() {
        String base = "gui.krios_industry.voice." + voiceKey() + ".";
        return switch (menu.stateCode()) {
            case ZoneMachineBlockEntity.STATE_WORKING -> Component.translatable(base + "working");
            case ZoneMachineBlockEntity.STATE_BUFFER_FULL -> Component.translatable(base + "buffer_full");
            default -> idlePhrase(menu.stateCode());
        };
    }
}
