package io.lakio.krios.industry.client.screen;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

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
    // panelX = leftPos - panelWidth - GAP(8) and the slot frame sits at
    // MODULE_SLOT_X - 1 = panelX + (panelWidth - 18) / 2. Width must stay
    // >= the localized title's minimum or the slots drift off-center.
    private static final int CONTENT_W = 58;
    /** Breathing room between the title bar and the first slot. */
    private static final int TOP_PAD = 5;
    static {
        // The bay heads the left stack (anchorsSlots): its slots sit at
        // STACK_TOP + title + TOP_PAD, plus the slot frame's 1px.
        if (MachineMenu.MODULE_SLOT_Y != STACK_TOP + TITLE_H + TOP_PAD + 1) {
            throw new IllegalStateException("MODULE_SLOT_Y out of sync with the annex stack");
        }
    }

    // Live supplier: the ACTIVE slot count follows the machine tier and
    // only syncs after the menu's first data broadcast (points model).
    private final IntSupplier slotCount;
    private final Consumer<Boolean> menuSync;

    public ModuleBayPanel(IntSupplier slotCount, Consumer<Boolean> menuSync) {
        super(Side.LEFT, "gui.krios_industry.modules.title", ClientPrefs.moduleBayOpen());
        this.slotCount = slotCount;
        this.menuSync = menuSync;
        // The fresh client menu defaults to open; align it with the pref.
        menuSync.accept(isOpen());
    }

    @Override
    protected boolean anchorsSlots() {
        return true;
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
        return TOP_PAD + Math.max(1, slotCount.getAsInt()) * MachineMenu.MODULE_SLOT_STEP - 4;
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
