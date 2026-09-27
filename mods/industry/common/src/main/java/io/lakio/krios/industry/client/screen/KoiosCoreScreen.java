package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class KoiosCoreScreen extends KriosTerminalScreen<KoiosCoreMenu> {

    private static final ResourceLocation TEXTURE = Constants.id("textures/gui/koios_core.png");
    private static final int XP_BAR_X = 8;
    private static final int XP_BAR_Y = 126;
    private static final int XP_BAR_WIDTH = 160;
    private static final int XP_BAR_HEIGHT = 10;

    // Palette A (graphics pass): light text on the dark GUI panel.
    private static final int COLOR_TEXT = 0xE8EAF0;
    private static final int COLOR_ONLINE = 0x31D8FF;
    private static final int COLOR_OFFLINE = 0xE5484D;
    private static final int COLOR_UNLOCKED = 0x31D8FF;
    private static final int COLOR_LOCKED = 0x5A6170;
    private static final int COLOR_CRITICAL = 0xFFB03A;

    public KoiosCoreScreen(KoiosCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected int terminalStatus() {
        return menu.isFormed() ? menu.getKoiosLevel() : 0;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
        int barX = leftPos + XP_BAR_X;
        int barY = topPos + XP_BAR_Y;
        if (mouseX >= barX && mouseX < barX + XP_BAR_WIDTH && mouseY >= barY && mouseY < barY + XP_BAR_HEIGHT) {
            guiGraphics.renderTooltip(font,
                    Component.translatable("gui.krios_industry.koios.xp", menu.getXp(), menu.getNextLevelXp()),
                    mouseX, mouseY);
        }
        renderTerminalOverlays(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
        int xpWidth = menu.getXpBarWidth(XP_BAR_WIDTH);
        if (xpWidth > 0) {
            guiGraphics.blit(TEXTURE, x + XP_BAR_X, y + XP_BAR_Y, 0, 166, xpWidth, XP_BAR_HEIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTerminalHeader(guiGraphics);

        if (menu.isFormed()) {
            guiGraphics.drawString(font, Component.translatable("gui.krios_industry.koios.online"),
                    8, 20, COLOR_ONLINE, false);
        } else {
            guiGraphics.drawString(font, Component.translatable("gui.krios_industry.koios.incomplete"),
                    8, 20, COLOR_OFFLINE, false);
        }

        guiGraphics.drawString(font, Component.translatable("gui.krios_industry.koios.level", menu.getKoiosLevel()),
                8, 34, COLOR_TEXT, false);
        guiGraphics.drawString(font,
                Component.translatable("gui.krios_industry.koios.diversity", menu.getDiversityPermille() / 10),
                8, 46, COLOR_TEXT, false);

        drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.speed", KoiosBalance.SPEED_UNLOCK_LEVEL, 62);
        drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.efficiency", KoiosBalance.EFFICIENCY_UNLOCK_LEVEL, 74);
        drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.yield", KoiosBalance.YIELD_UNLOCK_LEVEL, 86);

        if (menu.isCriticalPending()) {
            guiGraphics.drawWordWrap(font, Component.translatable("gui.krios_industry.koios.critical"),
                    8, 100, XP_BAR_WIDTH, COLOR_CRITICAL);
        }
    }

    private void drawUnlock(GuiGraphics guiGraphics, String nameKey, int requiredLevel, int y) {
        boolean unlocked = menu.getKoiosLevel() >= requiredLevel;
        Component line = Component.translatable("gui.krios_industry.koios.unlock",
                Component.translatable(nameKey), requiredLevel);
        guiGraphics.drawString(font, line, 8, y, unlocked ? COLOR_UNLOCKED : COLOR_LOCKED, false);
    }
}
