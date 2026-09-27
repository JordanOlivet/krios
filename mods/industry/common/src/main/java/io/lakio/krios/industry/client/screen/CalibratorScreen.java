package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.tool.CalibratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Network diagnostic readout (display-only, placeholder look until the
 * graphics pass — the future core network dashboard shares this style).
 */
public class CalibratorScreen extends AbstractContainerScreen<CalibratorMenu> {

    private static final int PANEL_COLOR = 0xF010141B;
    private static final int LINE_HEIGHT = 14;

    public CalibratorScreen(CalibratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 200;
        imageHeight = 110;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR);
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 16, 0xFF1B202A);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 6, 4, 0xFF31D8FF, false);
        int y = 22;
        int status = menu.status();
        Component statusLine = switch (status) {
            case CalibratorMenu.STATUS_ONLINE -> Component.translatable(
                    "gui.krios_industry.diag.status.online", menu.controllerLevel());
            case CalibratorMenu.STATUS_ORPHAN -> Component.translatable(
                    "gui.krios_industry.diag.status.orphan");
            case CalibratorMenu.STATUS_FAULT -> Component.translatable(
                    "gui.krios_industry.diag.status.fault");
            default -> Component.translatable("gui.krios_industry.diag.status.none");
        };
        int statusColor = switch (status) {
            case CalibratorMenu.STATUS_ONLINE -> 0xFF31D8FF;
            case CalibratorMenu.STATUS_FAULT -> 0xFFE5484D;
            default -> 0xFFFFB03A;
        };
        guiGraphics.drawString(font, statusLine, 6, y, statusColor, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.nodes",
                menu.machineCount(), menu.blockCount()), 6, y, 0xFFE8EAF0, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.energy",
                menu.energyStored(), menu.energyCapacity()), 6, y, 0xFFE8EAF0, false);
        y += LINE_HEIGHT;
        if (menu.hintMembers() > 0) {
            guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.hints",
                    menu.hintMembers()), 6, y, 0xFFFFB03A, false);
        }
    }
}
