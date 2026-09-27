package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.crusher.CrusherMenu;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.network.Flow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CrusherScreen extends KriosTerminalScreen<CrusherMenu> {

    private static final ResourceLocation TEXTURE = Constants.id("textures/gui/crusher.png");
    private static final int ENERGY_BAR_X = 8;
    private static final int ENERGY_BAR_Y = 17;
    private static final int ENERGY_BAR_WIDTH = 160;
    private static final int ENERGY_BAR_HEIGHT = 8;
    private static final int ARROW_X = 80;
    private static final int ARROW_Y = 42;
    private static final int ARROW_WIDTH = 22;
    private static final int ARROW_HEIGHT = 15;
    private static final int FACES_BUTTON_SIZE = 12;

    private final FaceConfigPanel facePanel = new FaceConfigPanel(
            new FaceConfigPanel.Column("gui.krios_industry.flow.energy_in", Flow.ENERGY, false),
            new FaceConfigPanel.Column("gui.krios_industry.flow.item_in", Flow.ITEM, false),
            new FaceConfigPanel.Column("gui.krios_industry.flow.item_out", Flow.ITEM, true));

    public CrusherScreen(CrusherMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected int terminalStatus() {
        return menu.networkLevel();
    }

    @Override
    protected int feedX() {
        // Left of the "F" faces button.
        return imageWidth - 34;
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
        // Faces toggle + side panel (placeholder look, graphics pass later).
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
        renderTerminalOverlays(guiGraphics, mouseX, mouseY);
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
        renderTerminalHeader(guiGraphics);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLOR_TEXT_DIM, false);
        // Live module effects (modules are visible container slots — no extra sync).
        int speed = 0;
        int efficiency = 0;
        int yieldCount = 0;
        for (int i = 2; i <= 3; i++) {
            if (menu.getSlot(i).getItem().getItem() instanceof ModuleItem module) {
                switch (module.getType()) {
                    case SPEED -> speed++;
                    case EFFICIENCY -> efficiency++;
                    case YIELD -> yieldCount++;
                }
            }
        }
        boolean online = menu.networkLevel() > 0;
        Component stats = Component.translatable("gui.krios_industry.term.crusher_stats",
                String.format("%.1f", (2 + speed) / 2.0),
                new String[]{"1.0", "0.8", "0.64"}[efficiency],
                yieldCount * 25);
        guiGraphics.drawString(font, stats, 8, 62, online ? COLOR_ACCENT_DIM : COLOR_TEXT_DIM, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
        int energyWidth = menu.getEnergyProgress(ENERGY_BAR_WIDTH);
        if (energyWidth > 0) {
            guiGraphics.blit(TEXTURE, x + ENERGY_BAR_X, y + ENERGY_BAR_Y, 0, 166, energyWidth, ENERGY_BAR_HEIGHT);
        }
        int arrowWidth = menu.getProgress(ARROW_WIDTH);
        if (arrowWidth > 0) {
            guiGraphics.blit(TEXTURE, x + ARROW_X, y + ARROW_Y, 176, 0, arrowWidth, ARROW_HEIGHT);
        }
    }
}
