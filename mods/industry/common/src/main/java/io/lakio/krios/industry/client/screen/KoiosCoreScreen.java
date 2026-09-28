package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
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

    private final KoiosArchivesPanel archives = new KoiosArchivesPanel();

    public KoiosCoreScreen(KoiosCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected int terminalStatus() {
        return menu.isFormed() ? menu.getKoiosLevel() : 0;
    }

    private int archivesButtonX() {
        return feedX() - 16;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
        int barX = leftPos + XP_BAR_X;
        int barY = topPos + XP_BAR_Y;
        if (!archives.isOpen()
                && mouseX >= barX && mouseX < barX + XP_BAR_WIDTH && mouseY >= barY && mouseY < barY + XP_BAR_HEIGHT) {
            guiGraphics.renderTooltip(font,
                    Component.translatable("gui.krios_industry.koios.xp", menu.getXp(), menu.getNextLevelXp()),
                    mouseX, mouseY);
        }
        // Variety gauge tooltip: the raw numbers behind the percentage.
        if (!archives.isOpen() && mouseX >= leftPos + 8 && mouseX < leftPos + 130
                && mouseY >= topPos + 45 && mouseY < topPos + 55) {
            guiGraphics.renderComponentTooltip(font, java.util.List.of(
                    Component.translatable("gui.krios_industry.koios.diversity.tooltip1",
                            menu.getDistinctOps(), menu.getWindowFill()),
                    Component.translatable("gui.krios_industry.koios.diversity.tooltip2",
                            KoiosBalance.DIVERSITY_FLOOR_PERMILLE / 10)),
                    mouseX, mouseY);
        }
        archives.render(guiGraphics, font, width, height, menu.getKoiosLevel(), mouseX, mouseY);
        int bx = leftPos + archivesButtonX();
        if (!archives.isOpen() && mouseX >= bx && mouseX < bx + 12
                && mouseY >= topPos + 4 && mouseY < topPos + 16) {
            guiGraphics.renderTooltip(font,
                    Component.translatable("gui.krios_industry.archive.title"), mouseX, mouseY);
        }
        renderTerminalOverlays(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (archives.click(width, height, mouseX, mouseY, menu.getKoiosLevel())) {
            return true;
        }
        int bx = leftPos + archivesButtonX();
        if (mouseX >= bx && mouseX < bx + 12 && mouseY >= topPos + 4 && mouseY < topPos + 16) {
            archives.toggleOpen();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        renderTerminalHeader(guiGraphics, mouseX, mouseY);
        // Archives button: a small book next to the feed.
        int bx = archivesButtonX();
        guiGraphics.fill(bx, 4, bx + 12, 16, archives.isOpen() ? 0xFF0A6E87 : 0xFF262B35);
        guiGraphics.fill(bx + 3, 6, bx + 9, 14, 0xFFE8EAF0);
        guiGraphics.fill(bx + 5, 6, bx + 6, 14, 0xFF262B35);

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
        } else {
            Component phrase = koiosPhrase();
            if (phrase != null) {
                guiGraphics.drawWordWrap(font, phrase.copy().withStyle(ChatFormatting.ITALIC),
                        8, 100, XP_BAR_WIDTH, COLOR_ACCENT_DIM);
            }
        }
    }

    /**
     * KOIOS's voice (art direction): one contextual line, rotating slowly so
     * it never spams. GUI only, never the chat.
     */
    private Component koiosPhrase() {
        if (!menu.isFormed()) {
            return null;
        }
        // Alerts first: energy health beats mood.
        if (menu.isEnergyDeficit()) {
            return Component.translatable("gui.krios_industry.koios.phrase.energy_deficit");
        }
        if (menu.getEnergyPermille() < 100) {
            return Component.translatable("gui.krios_industry.koios.phrase.energy_low");
        }
        if (menu.getDiversityPermille() < 400) {
            return Component.translatable("gui.krios_industry.koios.phrase.repetitive");
        }
        int next = menu.getNextLevelXp();
        if (next > 0 && menu.getXp() >= next * 0.8) {
            return Component.translatable("gui.krios_industry.koios.phrase.almost");
        }
        long slot = Util.getMillis() / 45_000 % 3;
        return Component.translatable("gui.krios_industry.koios.phrase.learning" + (slot + 1));
    }

    private void drawUnlock(GuiGraphics guiGraphics, String nameKey, int requiredLevel, int y) {
        boolean unlocked = menu.getKoiosLevel() >= requiredLevel;
        Component line = Component.translatable("gui.krios_industry.koios.unlock",
                Component.translatable(nameKey), requiredLevel);
        guiGraphics.drawString(font, line, 8, y, unlocked ? COLOR_UNLOCKED : COLOR_LOCKED, false);
    }
}
