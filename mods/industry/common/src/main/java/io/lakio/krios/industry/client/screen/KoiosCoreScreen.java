package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The KOIOS core terminal, codex-style (same visual language as the
 * Codex and the Calibrator dashboard): framed panel, standard header
 * (close cross rightmost, the AI's feed left of it, then the Codex
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

    private final CodexPanel codex = new CodexPanel();

    public KoiosCoreScreen(KoiosCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 216;
    }

    @Override
    protected int terminalStatus() {
        return menu.isFormed() ? menu.getKoiosLevel() : 0;
    }

    private int codexButtonX() {
        return imageWidth - 52;
    }

    private int xpBarWidth() {
        return imageWidth - 16;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
        boolean overlayOpen = codex.isOpen();
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
        codex.render(guiGraphics, font, width, height, menu,
                terminalStatus(), mouseX, mouseY);
        int bx = leftPos + codexButtonX();
        if (!overlayOpen && mouseX >= bx && mouseX < bx + 12
                && mouseY >= topPos + 4 && mouseY < topPos + 16) {
            guiGraphics.renderTooltip(font,
                    Component.translatable("gui.krios_industry.codex.title"), mouseX, mouseY);
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
        if (codex.click(width, height, mouseX, mouseY, menu)) {
            return true;
        }
        if (isCloseCrossHit(mouseX, mouseY)) {
            onClose();
            return true;
        }
        int bx = leftPos + codexButtonX();
        if (mouseX >= bx && mouseX < bx + 12 && mouseY >= topPos + 4 && mouseY < topPos + 16) {
            codex.toggleOpen();
            // Opening reports the visible-entry signature: the server-side
            // core remembers it and the badge goes out (menu button channel,
            // same plumbing as the machines' face buttons).
            if (codex.isOpen() && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        KoiosCoreMenu.CODEX_SEEN_BUTTON_BASE + CodexPanel.visibleSignature(menu));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (codex.scroll(width, height, mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        renderCodexFrame(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTerminalHeader(guiGraphics, mouseX, mouseY);
        // Header buttons are hidden while the Codex overlay is open: the
        // core panel is taller than the overlay, so they would poke above its
        // frame (and the overlay swallows every click anyway).
        if (!codex.isOpen()) {
            renderCloseCross(guiGraphics, mouseX, mouseY);
            // Codex button: a small book left of the feed.
            int bx = codexButtonX();
            guiGraphics.fill(bx, 4, bx + 12, 16, 0xFF262B35);
            guiGraphics.fill(bx + 3, 6, bx + 9, 14, 0xFFE8EAF0);
            guiGraphics.fill(bx + 5, 6, bx + 6, 14, 0xFF262B35);
            // Unread badge: entries appeared since the codex was last
            // opened (level unlocks, projections surfacing).
            if (CodexPanel.visibleSignature(menu) > menu.getCodexSeen()) {
                guiGraphics.fill(bx + 8, 2, bx + 13, 7, 0xFF0C0E13);
                guiGraphics.fill(bx + 9, 3, bx + 12, 6, 0xFFE5484D);
            }
        }

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
        // Hardware tier, right-aligned on the level line (dual axis reads
        // in one glance: software level left, hardware tier right).
        Component tierLine = Component.translatable("gui.krios_industry.koios.tier", menu.getTier(),
                Component.translatable("gui.krios_industry.koios.tier.name." + menu.getTier()));
        guiGraphics.drawString(font, tierLine, imageWidth - 8 - font.width(tierLine), y,
                COLOR_CRITICAL, false);
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
        // The CURRENT tier's ladder only — earlier tiers are trivially
        // acquired and their history belongs to the Codex Progression entry.
        if (menu.getTier() >= 2) {
            y = drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.speed2", KoiosBalance.SPEED2_UNLOCK_LEVEL, y);
            y = drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.energy", KoiosBalance.ENERGY_UNLOCK_LEVEL, y);
            y = drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.efficiency2", KoiosBalance.EFFICIENCY2_UNLOCK_LEVEL, y);
            y = drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.yield2", KoiosBalance.YIELD2_UNLOCK_LEVEL, y);
        } else {
            y = drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.speed", KoiosBalance.SPEED_UNLOCK_LEVEL, y);
            y = drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.efficiency", KoiosBalance.EFFICIENCY_UNLOCK_LEVEL, y);
            y = drawUnlock(guiGraphics, "gui.krios_industry.koios.unlock.yield", KoiosBalance.YIELD_UNLOCK_LEVEL, y);
        }
        y += 3;

        // Critical notice (the voice lives under the XP gauge, terminal-style):
        // actionable when the next tier exists, "coming soon" otherwise.
        if (menu.isCriticalPending()) {
            guiGraphics.fill(8, y, imageWidth - 8, y + 1, 0xFF262B35);
            y += 6;
            Component notice = menu.isBreakthroughAvailable()
                    ? Component.translatable("gui.krios_industry.koios.critical.ready",
                            KoiosBalance.criticalLevel(menu.getTier()))
                    : Component.translatable("gui.krios_industry.koios.critical.locked",
                            KoiosBalance.criticalLevel(menu.getTier()), menu.getTier() + 1);
            guiGraphics.drawWordWrap(font, notice, 8, y, imageWidth - 16, COLOR_CRITICAL);
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
