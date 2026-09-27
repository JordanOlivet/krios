package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.generator.GeneratorMenu;
import io.lakio.krios.industry.network.Flow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {

    private static final ResourceLocation TEXTURE = Constants.id("textures/gui/generator.png");
    private static final int ENERGY_BAR_X = 8;
    private static final int ENERGY_BAR_Y = 17;
    private static final int ENERGY_BAR_WIDTH = 160;
    private static final int ENERGY_BAR_HEIGHT = 8;
    private static final int FACES_BUTTON_SIZE = 12;

    private final FaceConfigPanel facePanel = new FaceConfigPanel(
            new FaceConfigPanel.Column("gui.krios_industry.flow.energy_out", Flow.ENERGY, true),
            new FaceConfigPanel.Column("gui.krios_industry.flow.item_in", Flow.ITEM, false));

    public GeneratorScreen(GeneratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private int facesButtonX() {
        return leftPos + imageWidth - FACES_BUTTON_SIZE - 4;
    }

    private int facesButtonY() {
        return topPos + 4;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(facesButtonX(), facesButtonY(),
                facesButtonX() + FACES_BUTTON_SIZE, facesButtonY() + FACES_BUTTON_SIZE,
                facePanel.isOpen() ? 0xFF0A6E87 : 0xFF262B35);
        guiGraphics.drawCenteredString(font, "F",
                facesButtonX() + FACES_BUTTON_SIZE / 2, facesButtonY() + 2, 0xFFE8EAF0);
        facePanel.render(guiGraphics, font, leftPos + imageWidth + 2, topPos, menu::faceBits);
        renderTooltip(guiGraphics, mouseX, mouseY);
        if (mouseX >= facesButtonX() && mouseX < facesButtonX() + FACES_BUTTON_SIZE
                && mouseY >= facesButtonY() && mouseY < facesButtonY() + FACES_BUTTON_SIZE) {
            guiGraphics.renderTooltip(font,
                    Component.translatable("gui.krios_industry.faces.title"), mouseX, mouseY);
        }
        int barX = leftPos + ENERGY_BAR_X;
        int barY = topPos + ENERGY_BAR_Y;
        if (mouseX >= barX && mouseX < barX + ENERGY_BAR_WIDTH && mouseY >= barY && mouseY < barY + ENERGY_BAR_HEIGHT) {
            guiGraphics.renderTooltip(font,
                    Component.literal(menu.getEnergyStored() + " / " + menu.getEnergyCapacity() + " E"),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= facesButtonX() && mouseX < facesButtonX() + FACES_BUTTON_SIZE
                && mouseY >= facesButtonY() && mouseY < facesButtonY() + FACES_BUTTON_SIZE) {
            facePanel.toggleOpen();
            return true;
        }
        int id = facePanel.click(leftPos + imageWidth + 2, topPos, mouseX, mouseY, menu::faceBits);
        if (id >= 0) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Palette A: light labels on the dark GUI panel.
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xE8EAF0, false);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x5A6170, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
        // Energy gauge fill (row stored at v=166 in the texture).
        int energyWidth = menu.getEnergyProgress(ENERGY_BAR_WIDTH);
        if (energyWidth > 0) {
            guiGraphics.blit(TEXTURE, x + ENERGY_BAR_X, y + ENERGY_BAR_Y, 0, 166, energyWidth, ENERGY_BAR_HEIGHT);
        }
        // Flame while burning (14x14 sprite at u=176 v=0, drawn bottom-up).
        if (menu.isBurning()) {
            int burn = menu.getBurnProgress(13);
            guiGraphics.blit(TEXTURE, x + 81, y + 26 + 13 - burn, 176, 13 - burn, 14, burn + 1);
        }
    }
}
