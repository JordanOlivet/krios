package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.machine.MachineMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Shared machine terminal (art direction, ADR-014): 256-wide codex frame
 * drawn in code, standard header (title, feed, close cross — no buttons),
 * vertical energy gauge on the left rail, machine work area in the center
 * (kept free for future tiers), contextual KOIOS voice line, centered
 * player inventory. Stats / face-config / module-bay live as collapsible
 * annex terminals docked on the sides, toggled on the panels themselves.
 */
public abstract class MachineTerminalScreen<T extends AbstractContainerMenu & MachineMenu>
        extends KriosTerminalScreen<T> {

    /** Center of the 256px work area, for machine layouts. */
    protected static final int WORK_CENTER_X = 128;
    /**
     * Two wrapped voice lines (2 x 9px) must fit above the inventory label
     * at y=104 with a 2px gap: long phrases and translations wrap instead
     * of overlapping the label (user call 2026-10-05, seen on the array
     * terminal). The work area above ends by y=85 on every machine.
     */
    private static final int VOICE_Y = 86;

    // Input-distribution toggle, left of the process columns (machines
    // with parallel columns only).
    private static final int DIST_X = 82;
    private static final int DIST_Y = 46;
    private static final int DIST_SIZE = 14;

    /** Machines with parallel process columns show the distribution toggle. */
    protected boolean hasDistributionToggle() {
        return false;
    }

    // Energy gauge geometry: vertical on the left rail by default; the
    // reactor overrides everything — its gauge is the showcase, a wide
    // horizontal bar across the top (agreed layout, kept through ADR-014).
    protected int energyBarX() {
        return 8;
    }

    protected int energyBarY() {
        return 24;
    }

    protected int energyBarWidth() {
        return 8;
    }

    protected int energyBarHeight() {
        // Bottom edge flush with the process output cells (y=64 content,
        // cell row 80), clearing the two-line voice zone that starts at 86.
        return 57;
    }

    protected boolean energyBarHorizontal() {
        return false;
    }

    protected MachineTerminalScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 194;
        inventoryLabelX = 47;
        inventoryLabelY = 104;
    }

    /** All annex panels, dock order; the face panel is announced separately. */
    protected abstract AnnexPanel[] annexPanels();

    protected abstract HologramFacePanel facePanel();

    /** Module bay when the machine has one; null otherwise (reactor). */
    protected ModuleBayPanel moduleBay() {
        return null;
    }

    /** Machine-specific center work area (progress arrow, flame...). */
    protected abstract void renderWork(GuiGraphics gui);

    /** Working-state phrase with the processed item's name; null when idle. */
    protected abstract Component workingPhrase();

    @Override
    protected int terminalStatus() {
        return menu.networkLevel();
    }

    /**
     * Contextual KOIOS voice: fault > starved > current operation >
     * offline > slow idle rotation. Everything reads synced menu state —
     * no custom networking.
     */
    protected Component voicePhrase() {
        int status = terminalStatus();
        if (status < 0) {
            return Component.translatable("gui.krios_industry.voice.fault");
        }
        if (menu.isStarved()) {
            return Component.translatable("gui.krios_industry.voice.starved");
        }
        Component working = workingPhrase();
        if (working != null) {
            return working;
        }
        if (status == 0) {
            return Component.translatable("gui.krios_industry.voice.offline");
        }
        long slot = Util.getMillis() / 45_000 % 3;
        return Component.translatable("gui.krios_industry.voice.idle" + (slot + 1));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        AnnexPanel.stack(annexPanels());
        HologramFacePanel faces = facePanel();
        faces.updateHover(faces.contentX(leftPos, imageWidth), faces.contentY(topPos), mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        for (AnnexPanel panel : annexPanels()) {
            // The module bay chassis renders early (renderBg) so the vanilla
            // slot pass draws its items on top; only its tooltip comes late.
            // The face panel renders through renderPanel (live payload).
            if (panel != moduleBay() && panel != faces) {
                panel.render(guiGraphics, font, leftPos, topPos, imageWidth, mouseX, mouseY);
            }
        }
        faces.renderPanel(guiGraphics, font, leftPos, topPos, imageWidth,
                menu::faceBits, menu.machinePos(), mouseX, mouseY);
        renderTooltip(guiGraphics, mouseX, mouseY);
        for (AnnexPanel panel : annexPanels()) {
            panel.renderTabTooltip(guiGraphics, font, leftPos, topPos, imageWidth, mouseX, mouseY);
        }
        int barX = leftPos + energyBarX();
        int barY = topPos + energyBarY();
        if (mouseX >= barX && mouseX < barX + energyBarWidth()
                && mouseY >= barY && mouseY < barY + energyBarHeight()) {
            guiGraphics.renderTooltip(font,
                    Component.literal(menu.getEnergyStored() + " / " + menu.getEnergyCapacity() + " E"),
                    mouseX, mouseY);
        }
        if (isDistributionToggleHit(mouseX, mouseY)) {
            guiGraphics.renderTooltip(font, Component.translatable(menu.isInputBalance()
                    ? "gui.krios_industry.distribution.balance"
                    : "gui.krios_industry.distribution.fill_first"), mouseX, mouseY);
        }
        renderTerminalOverlays(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        renderCodexFrame(guiGraphics);
        ModuleBayPanel bay = moduleBay();
        if (bay != null) {
            bay.render(guiGraphics, font, leftPos, topPos, imageWidth, mouseX, mouseY);
        }
        // Codex slot frames for every active slot (replaces the old texture).
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                drawSlotFrame(guiGraphics, leftPos + slot.x - 1, topPos + slot.y - 1);
            }
        }
        // Energy gauge (local buffer, amber): vertical fills bottom-up,
        // horizontal fills left-to-right, bright line on the leading edge.
        int x = leftPos + energyBarX();
        int y = topPos + energyBarY();
        int w = energyBarWidth();
        int h = energyBarHeight();
        guiGraphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0C0E13);
        guiGraphics.fill(x, y, x + w, y + h, 0xFF12151B);
        if (energyBarHorizontal()) {
            int fill = menu.getEnergyProgress(w);
            if (fill > 0) {
                guiGraphics.fill(x, y, x + fill, y + h, 0xFFFFB03A);
                guiGraphics.fill(x + fill - 1, y, x + fill, y + h, 0xFFFFD98A);
            }
        } else {
            int fill = menu.getEnergyProgress(h);
            if (fill > 0) {
                guiGraphics.fill(x, y + h - fill, x + w, y + h, 0xFFFFB03A);
                guiGraphics.fill(x, y + h - fill, x + w, y + h - fill + 1, 0xFFFFD98A);
            }
        }
        renderWork(guiGraphics);
        if (hasDistributionToggle()) {
            drawDistributionToggle(guiGraphics);
        }
    }

    /**
     * Input-distribution toggle: two mini input bars — fill first shows a
     * full and an empty one, balance shows two half-filled equals.
     */
    private void drawDistributionToggle(GuiGraphics gui) {
        int x = leftPos + DIST_X;
        int y = topPos + DIST_Y;
        gui.fill(x, y, x + DIST_SIZE, y + DIST_SIZE, 0xFF0C0E13);
        gui.fill(x + 1, y + 1, x + DIST_SIZE - 1, y + DIST_SIZE - 1, 0xFF1B202A);
        boolean balance = menu.isInputBalance();
        // Left and right mini bars, filled from the bottom.
        int leftFill = balance ? 5 : 10;
        int rightFill = balance ? 5 : 0;
        gui.fill(x + 3, y + 2, x + 6, y + 12, 0xFF262B35);
        gui.fill(x + 8, y + 2, x + 11, y + 12, 0xFF262B35);
        if (leftFill > 0) {
            gui.fill(x + 3, y + 12 - leftFill, x + 6, y + 12, 0xFF31D8FF);
        }
        if (rightFill > 0) {
            gui.fill(x + 8, y + 12 - rightFill, x + 11, y + 12, 0xFF31D8FF);
        }
    }

    private boolean isDistributionToggleHit(double mouseX, double mouseY) {
        int x = leftPos + DIST_X;
        int y = topPos + DIST_Y;
        return hasDistributionToggle() && mouseX >= x && mouseX < x + DIST_SIZE
                && mouseY >= y && mouseY < y + DIST_SIZE;
    }

    /** 18x18 codex slot frame at absolute (x, y). */
    protected static void drawSlotFrame(GuiGraphics gui, int x, int y) {
        gui.fill(x, y, x + 18, y + 18, 0xFF0C0E13);
        gui.fill(x + 1, y + 1, x + 17, y + 17, 0xFF1B202A);
    }

    /**
     * Vertical process-column bar (Mekanism-style): dim skeleton, cyan
     * fill growing downward from input to output. x is the COLUMN's slot
     * x (18px); the 6px bar centers itself.
     */
    protected static void drawColumnProgress(GuiGraphics gui, int x, int y, int height, int progress) {
        int barX = x + 6;
        gui.fill(barX, y, barX + 6, y + height, 0xFF262B35);
        if (progress > 0) {
            gui.fill(barX, y, barX + 6, y + Math.min(height, progress), 0xFF31D8FF);
            gui.fill(barX, y + Math.min(height, progress) - 1, barX + 6,
                    y + Math.min(height, progress), 0xFF9FF0FF);
        }
    }

    /**
     * Code-drawn progress arrow, 22x15 at absolute (x, y): dim skeleton,
     * cyan fill clipped to progress.
     */
    protected static void drawProgressArrow(GuiGraphics gui, int x, int y, int progress) {
        drawArrowShape(gui, x, y, 0xFF262B35);
        if (progress > 0) {
            gui.enableScissor(x, y, x + Math.min(22, progress), y + 15);
            drawArrowShape(gui, x, y, 0xFF31D8FF);
            gui.disableScissor();
        }
    }

    private static void drawArrowShape(GuiGraphics gui, int x, int y, int color) {
        gui.fill(x, y + 5, x + 15, y + 10, color);
        gui.fill(x + 15, y + 2, x + 17, y + 13, color);
        gui.fill(x + 17, y + 4, x + 19, y + 11, color);
        gui.fill(x + 19, y + 6, x + 22, y + 9, color);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTerminalHeader(guiGraphics, mouseX, mouseY);
        renderCloseCross(guiGraphics, mouseX, mouseY);
        Component phrase = voicePhrase();
        if (phrase != null) {
            drawVoiceLine(guiGraphics, phrase, 8, VOICE_Y, imageWidth - 16);
        }
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                COLOR_TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isCloseCrossHit(mouseX, mouseY)) {
            onClose();
            return true;
        }
        AnnexPanel.stack(annexPanels());
        if (isDistributionToggleHit(mouseX, mouseY)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    MachineMenu.BUTTON_TOGGLE_DISTRIBUTION);
            return true;
        }
        for (AnnexPanel panel : annexPanels()) {
            if (panel.chassisClick(leftPos, topPos, imageWidth, mouseX, mouseY)) {
                return true;
            }
        }
        HologramFacePanel faces = facePanel();
        int id = faces.click(faces.contentX(leftPos, imageWidth), faces.contentY(topPos),
                mouseX, mouseY, button, menu::faceBits);
        if (id >= 0) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            return true;
        }
        if (id == HologramFacePanel.CLICK_CONSUMED) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        AnnexPanel.stack(annexPanels());
        HologramFacePanel faces = facePanel();
        if (faces.mouseDragged(faces.contentX(leftPos, imageWidth), faces.contentY(topPos),
                mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
