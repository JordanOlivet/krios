package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The KOIOS core terminal, codex-style (same visual language as the
 * Archives and the Calibrator dashboard): framed panel, standard header
 * (close cross rightmost, the AI's feed left of it, then the Archives
 * button), status/progression sections and a full-width data-XP gauge.
 */
public class KoiosCoreScreen extends KriosTerminalScreen<KoiosCoreMenu> {

    private static final int LINE_HEIGHT = 12;
    private static final int XP_BAR_X = 8;
    private static final int XP_BAR_Y = 182;
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
        imageWidth = 256;
        imageHeight = 216;
    }

    @Override
    protected int terminalStatus() {
        return menu.isFormed() ? menu.getKoiosLevel() : 0;
    }

    @Override
    protected int feedX() {
        // Standard header order: close cross rightmost, feed to its left.
        return imageWidth - 34;
    }

    private int archivesButtonX() {
        return imageWidth - 52;
    }

    private int xpBarWidth() {
        return imageWidth - 16;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
        boolean overlayOpen = archives.isOpen();
        // XP gauge tooltip.
        int barX = leftPos + XP_BAR_X;
        int barY = topPos + XP_BAR_Y;
        if (!overlayOpen
                && mouseX >= barX && mouseX < barX + xpBarWidth() && mouseY >= barY && mouseY < barY + XP_BAR_HEIGHT) {
            guiGraphics.renderTooltip(font,
                    Component.translatable("gui.krios_industry.koios.xp", menu.getXp(), menu.getNextLevelXp()),
                    mouseX, mouseY);
        }
        // Variety gauge tooltip: the raw numbers behind the percentage.
        if (!overlayOpen && mouseX >= leftPos + 8 && mouseX < leftPos + 170
                && mouseY >= topPos + 48 && mouseY < topPos + 58) {
            guiGraphics.renderComponentTooltip(font, java.util.List.of(
                    Component.translatable("gui.krios_industry.koios.diversity.tooltip1",
                            menu.getDistinctOps(), menu.getWindowFill()),
                    Component.translatable("gui.krios_industry.koios.diversity.tooltip2",
                            KoiosBalance.DIVERSITY_FLOOR_PERMILLE / 10)),
                    mouseX, mouseY);
        }
        archives.render(guiGraphics, font, width, height, menu.getKoiosLevel(),
                terminalStatus(), mouseX, mouseY);
        int bx = leftPos + archivesButtonX();
        if (!overlayOpen && mouseX >= bx && mouseX < bx + 12
                && mouseY >= topPos + 4 && mouseY < topPos + 16) {
            guiGraphics.renderTooltip(font,
                    Component.translatable("gui.krios_industry.archive.title"), mouseX, mouseY);
        }
        // Suppress the chrome's feed tooltip while the overlay covers it.
        if (overlayOpen) {
            renderTerminalOverlays(guiGraphics, Integer.MIN_VALUE, Integer.MIN_VALUE);
        } else {
            renderTerminalOverlays(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (archives.click(width, height, mouseX, mouseY, menu.getKoiosLevel())) {
            return true;
        }
        // Close cross, rightmost.
        if (mouseX >= leftPos + imageWidth - 16 && mouseX < leftPos + imageWidth - 4
                && mouseY >= topPos + 4 && mouseY < topPos + 16) {
            onClose();
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
        // Codex-style framed panel (shared visual language).
        guiGraphics.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF0A6E87);
        guiGraphics.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, 0xFF0C0E13);
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF10141B);
        guiGraphics.fill(x, y, x + imageWidth, y + 18, 0xFF1B202A);
        guiGraphics.fill(x, y + 18, x + imageWidth, y + 19, 0xFF0A6E87);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTerminalHeader(guiGraphics, mouseX, mouseY);
        // Close cross, rightmost (standard header).
        boolean closeHover = mouseX - leftPos >= imageWidth - 16 && mouseX - leftPos < imageWidth - 4
                && mouseY - topPos >= 4 && mouseY - topPos < 16;
        guiGraphics.fill(imageWidth - 16, 4, imageWidth - 4, 16, closeHover ? 0xFF6B2F2F : 0xFF262B35);
        guiGraphics.drawCenteredString(font, "X", imageWidth - 10, 6, 0xFFE8EAF0);
        // Archives button: a small book left of the feed.
        int bx = archivesButtonX();
        guiGraphics.fill(bx, 4, bx + 12, 16, archives.isOpen() ? 0xFF0A6E87 : 0xFF262B35);
        guiGraphics.fill(bx + 3, 6, bx + 9, 14, 0xFFE8EAF0);
        guiGraphics.fill(bx + 5, 6, bx + 6, 14, 0xFF262B35);

        // Status, level, variety.
        int y = 25;
        if (menu.isFormed()) {
            guiGraphics.drawString(font, Component.translatable("gui.krios_industry.koios.online"),
                    8, y, COLOR_ONLINE, false);
        } else {
            guiGraphics.drawString(font, Component.translatable("gui.krios_industry.koios.incomplete"),
                    8, y, COLOR_OFFLINE, false);
        }
        y += LINE_HEIGHT;
        guiGraphics.drawString(font, Component.translatable("gui.krios_industry.koios.level", menu.getKoiosLevel()),
                8, y, COLOR_TEXT, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(font,
                Component.translatable("gui.krios_industry.koios.diversity", menu.getDiversityPermille() / 10),
                8, y, 0xFFA9AFBD, false);
        y += LINE_HEIGHT + 3;

        // Unlocks section.
        guiGraphics.fill(8, y, imageWidth - 8, y + 1, 0xFF262B35);
        y += 6;
        guiGraphics.drawString(font, Component.translatable("gui.krios_industry.koios.section.unlocks"),
                8, y, COLOR_LOCKED, false);
        y += LINE_HEIGHT;
        y = drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.speed", KoiosBalance.SPEED_UNLOCK_LEVEL, y);
        y = drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.efficiency", KoiosBalance.EFFICIENCY_UNLOCK_LEVEL, y);
        y = drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.yield", KoiosBalance.YIELD_UNLOCK_LEVEL, y);
        y += 3;

        // Critical notice (the voice lives under the XP gauge, terminal-style).
        if (menu.isCriticalPending()) {
            guiGraphics.fill(8, y, imageWidth - 8, y + 1, 0xFF262B35);
            y += 6;
            guiGraphics.drawWordWrap(font, Component.translatable("gui.krios_industry.koios.critical"),
                    8, y, imageWidth - 16, COLOR_CRITICAL);
        }

        // Data-XP gauge, bottom, full width.
        Component xpCaption = Component.translatable("gui.krios_industry.koios.section.xp");
        guiGraphics.drawString(font, xpCaption, 8, XP_BAR_Y - 11, COLOR_LOCKED, false);
        Component xpNumbers = Component.translatable("gui.krios_industry.koios.xp",
                menu.getXp(), menu.getNextLevelXp());
        guiGraphics.drawString(font, xpNumbers, imageWidth - 8 - font.width(xpNumbers),
                XP_BAR_Y - 11, COLOR_TEXT, false);
        int barW = xpBarWidth();
        guiGraphics.fill(XP_BAR_X, XP_BAR_Y, XP_BAR_X + barW, XP_BAR_Y + XP_BAR_HEIGHT, 0xFF0C0E13);
        guiGraphics.fill(XP_BAR_X + 1, XP_BAR_Y + 1, XP_BAR_X + barW - 1, XP_BAR_Y + XP_BAR_HEIGHT - 1, 0xFF12151B);
        int fill = menu.getXpBarWidth(barW - 2);
        if (fill > 0) {
            guiGraphics.fill(XP_BAR_X + 1, XP_BAR_Y + 1, XP_BAR_X + 1 + fill, XP_BAR_Y + XP_BAR_HEIGHT - 1, 0xFF31D8FF);
            guiGraphics.fill(XP_BAR_X + 1, XP_BAR_Y + 1, XP_BAR_X + 1 + fill, XP_BAR_Y + 2, 0xFF9FF0FF);
        }

        // KOIOS's voice, terminal-style, under the gauge: "> phrase_" with a
        // blinking cursor.
        Component phrase = koiosPhrase();
        if (phrase != null) {
            String cursor = (Util.getMillis() / 500) % 2 == 0 ? "_" : "";
            Component line = Component.literal("> ").append(phrase).append(Component.literal(cursor));
            guiGraphics.drawWordWrap(font, line, 8, XP_BAR_Y + XP_BAR_HEIGHT + 5,
                    imageWidth - 16, 0xFF5FC8DC);
        }
    }

    private int drawUnlock(GuiGraphics guiGraphics, String nameKey, int requiredLevel, int y) {
        boolean unlocked = menu.getKoiosLevel() >= requiredLevel;
        Component line = Component.translatable("gui.krios_industry.koios.unlock",
                Component.translatable(nameKey), requiredLevel);
        guiGraphics.drawString(font, line, 8, y, unlocked ? COLOR_UNLOCKED : COLOR_LOCKED, false);
        return y + LINE_HEIGHT;
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
}
