package io.lakio.krios.industry.client.screen;

import java.util.function.Consumer;

import io.lakio.krios.industry.client.ClientPrefs;
import io.lakio.krios.industry.machine.MachineMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Module bay annex: hosts the machine's REAL module slots outside the main
 * frame, so the slot count can grow with tiers without eating the work
 * area. The chassis is decorative — the slots themselves are vanilla menu
 * slots whose coordinates ({@link MachineMenu#MODULE_SLOT_X}...) target
 * this panel, and whose isActive() follows the open state through the
 * client menu instance (collapsed bay = hidden, uninteractable slots;
 * server-side shift-click routing is unaffected).
 */
public class ModuleBayPanel extends AnnexPanel {

    // Geometry contract with MachineMenu's slot constants:
    // panelX = leftPos - panelWidth - 3 and the slot frame sits at
    // MODULE_SLOT_X - 1 = panelX + (panelWidth - 18) / 2.
    private static final int CONTENT_W = 50;
    /** Slot frame top = contentY = dockY + TITLE_H. */
    private static final int DOCK_Y = MachineMenu.MODULE_SLOT_Y - 1 - TITLE_H;

    private final int slotCount;
    private final Consumer<Boolean> menuSync;

    public ModuleBayPanel(int slotCount, Consumer<Boolean> menuSync) {
        super(Side.LEFT, DOCK_Y, "gui.krios_industry.modules.title", ClientPrefs.moduleBayOpen());
        this.slotCount = slotCount;
        this.menuSync = menuSync;
        // The fresh client menu defaults to open; align it with the pref.
        menuSync.accept(isOpen());
    }

    @Override
    protected void onToggle(boolean open) {
        ClientPrefs.setModuleBayOpen(open);
        menuSync.accept(open);
    }

    @Override
    protected int contentWidth() {
        return CONTENT_W;
    }

    @Override
    protected int contentHeight() {
        return slotCount * MachineMenu.MODULE_SLOT_STEP - 4;
    }

    @Override
    protected void renderContent(GuiGraphics gui, Font font, int x, int y, int mouseX, int mouseY) {
        // Slot frames and items are drawn by the screen's vanilla slot pass.
    }

    @Override
    protected void drawTabIcon(GuiGraphics gui, int x, int y) {
        // A module chip: body + two contact pins.
        gui.fill(x + 1, y, x + 7, y + 6, 0xFF31D8FF);
        gui.fill(x + 2, y + 1, x + 6, y + 5, 0xFF10141B);
        gui.fill(x + 3, y + 2, x + 5, y + 4, 0xFFFFB03A);
        gui.fill(x + 2, y + 6, x + 3, y + 8, 0xFFB87333);
        gui.fill(x + 5, y + 6, x + 6, y + 8, 0xFFB87333);
    }
}
