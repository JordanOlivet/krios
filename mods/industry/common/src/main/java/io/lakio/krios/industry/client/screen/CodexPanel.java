package io.lakio.krios.industry.client.screen;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The Codex (art direction; renamed from "KOIOS Archives" 2026-10-05): a
 * living compendium, KOIOS explaining its own ecosystem in first person. Rendered as a screen-centered overlay above
 * the core terminal (no screen stack, the container never closes): dimmed
 * backdrop, section sidebar on the left, one readable page on the right.
 * Entries unlock and grow richer with the core's level — the guide's
 * progression IS the AI's progression. Client-only; all content in lang.
 */
public class CodexPanel {

    /** id → lang keys codex.<id>.title/.body(/.adv); minLevel gates it. */
    private record Entry(String id, int minLevel, boolean hasAdvanced) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("koios", 1, true),
            new Entry("diversity", 1, false),
            new Entry("generator", 1, false),
            new Entry("solar_panel", 1, false),
            new Entry("crusher", 1, false),
            new Entry("furnace", 1, false),
            new Entry("modules", 2, true),
            new Entry("network", 1, true),
            new Entry("conduit", 1, false),
            new Entry("calibrator", 1, false),
            new Entry("critical", 4, false));

    /** Level at which the advanced paragraphs appear. */
    private static final int ADVANCED_LEVEL = 3;

    private static final int W = 256;
    private static final int H = 200;
    private static final int HEADER_H = 18;
    private static final int SIDEBAR_X = 6;
    private static final int SIDEBAR_W = 82;
    private static final int ROW_H = 16;
    private static final int PAGE_X = 96;
    private static final int PAGE_W = W - PAGE_X - 10;

    private boolean open;
    private int selected;

    public boolean isOpen() {
        return open;
    }

    public void toggleOpen() {
        open = !open;
    }

    private int panelX(int screenW) {
        return (screenW - W) / 2;
    }

    private int panelY(int screenH) {
        return (screenH - H) / 2;
    }

    public void render(GuiGraphics gui, Font font, int screenW, int screenH, int koiosLevel,
            int feedStatus, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        int x = panelX(screenW);
        int y = panelY(screenH);

        // Lift the whole overlay above the host screen's chrome (items sit at
        // z~232, tooltips at 400): without this the terminal's header glyphs
        // bleed through the panel frame.
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 300);

        // Dimmed backdrop + framed panel: this reads as "a codex", not a popup.
        gui.fill(0, 0, screenW, screenH, 0xB0000000);
        gui.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF0A6E87);
        gui.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFF0C0E13);
        gui.fill(x, y, x + W, y + H, 0xFF10141B);

        // Header band — standard order: close cross rightmost, feed left of it.
        gui.fill(x, y, x + W, y + HEADER_H, 0xFF1B202A);
        gui.fill(x, y + HEADER_H, x + W, y + HEADER_H + 1, 0xFF0A6E87);
        gui.drawString(font, Component.translatable("gui.krios_industry.codex.title"),
                x + 8, y + 5, 0xFF31D8FF, false);
        boolean closeHover = mouseX >= x + W - 16 && mouseX < x + W - 4
                && mouseY >= y + 3 && mouseY < y + 15;
        gui.fill(x + W - 16, y + 3, x + W - 4, y + 15, closeHover ? 0xFF6B2F2F : 0xFF262B35);
        gui.drawCenteredString(font, Component.literal("X"), x + W - 10, y + 5, 0xFFE8EAF0);
        KriosTerminalScreen.drawFeedBox(gui, x + W - 34, y + 3, feedStatus, mouseX, mouseY);

        // Sidebar: the section list.
        gui.fill(x + SIDEBAR_X + SIDEBAR_W + 3, y + HEADER_H + 4,
                x + SIDEBAR_X + SIDEBAR_W + 4, y + H - 6, 0xFF262B35);
        Component tooltip = null;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            int rowY = y + HEADER_H + 6 + i * ROW_H;
            boolean locked = koiosLevel < entry.minLevel();
            boolean hover = mouseX >= x + SIDEBAR_X && mouseX < x + SIDEBAR_X + SIDEBAR_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (i == selected && !locked) {
                gui.fill(x + SIDEBAR_X, rowY, x + SIDEBAR_X + SIDEBAR_W, rowY + ROW_H - 2, 0xFF0A2A34);
                gui.fill(x + SIDEBAR_X, rowY, x + SIDEBAR_X + 2, rowY + ROW_H - 2, 0xFF31D8FF);
            } else if (hover && !locked) {
                gui.fill(x + SIDEBAR_X, rowY, x + SIDEBAR_X + SIDEBAR_W, rowY + ROW_H - 2, 0xFF171B22);
            }
            int color = locked ? 0xFF31363F : i == selected ? 0xFF31D8FF : 0xFFA9AFBD;
            // Titles too wide for the column are ellipsized; hover shows the
            // full name (a locked notice wins over it).
            Component title = Component.translatable(
                    "gui.krios_industry.codex." + entry.id() + ".title");
            int maxWidth = SIDEBAR_W - 12;
            boolean truncated = font.width(title) > maxWidth;
            Component shown = truncated
                    ? Component.literal(font.plainSubstrByWidth(
                            title.getString(), maxWidth - font.width("…")) + "…")
                    : title;
            gui.drawString(font, shown, x + SIDEBAR_X + 6, rowY + 4, color, false);
            if (hover) {
                if (locked) {
                    tooltip = Component.translatable("gui.krios_industry.codex.locked", entry.minLevel());
                } else if (truncated) {
                    tooltip = title;
                }
            }
        }

        // The page.
        Entry entry = ENTRIES.get(selected);
        int textX = x + PAGE_X;
        int textY = y + HEADER_H + 8;
        gui.drawString(font, Component.translatable(
                "gui.krios_industry.codex." + entry.id() + ".title"),
                textX, textY, 0xFFE8EAF0, false);
        gui.fill(textX, textY + 11, textX + PAGE_W, textY + 12, 0xFF0A6E87);
        textY += 18;
        Component body = Component.translatable("gui.krios_industry.codex." + entry.id() + ".body");
        gui.drawWordWrap(font, body, textX, textY, PAGE_W, 0xFFA9AFBD);
        textY += font.split(body, PAGE_W).size() * 9 + 10;
        if (entry.hasAdvanced() && koiosLevel >= ADVANCED_LEVEL) {
            Component adv = Component.translatable(
                    "gui.krios_industry.codex." + entry.id() + ".adv")
                    .copy().withStyle(ChatFormatting.ITALIC);
            int advHeight = font.split(adv, PAGE_W - 6) .size() * 9;
            gui.fill(textX, textY, textX + 1, textY + advHeight, 0xFF0A6E87);
            gui.drawWordWrap(font, adv, textX + 6, textY, PAGE_W - 6, 0xFF5FA8BC);
        }
        if (tooltip != null) {
            gui.renderTooltip(font, tooltip, mouseX, mouseY);
        }
        gui.pose().popPose();
    }

    /** True when the click was handled (the overlay swallows every click). */
    public boolean click(int screenW, int screenH, double mouseX, double mouseY, int koiosLevel) {
        if (!open) {
            return false;
        }
        int x = panelX(screenW);
        int y = panelY(screenH);
        if (mouseX >= x + W - 16 && mouseX < x + W - 4 && mouseY >= y + 3 && mouseY < y + 15) {
            open = false;
            return true;
        }
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            int rowY = y + HEADER_H + 6 + i * ROW_H;
            if (mouseX >= x + SIDEBAR_X && mouseX < x + SIDEBAR_X + SIDEBAR_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H
                    && koiosLevel >= entry.minLevel()) {
                selected = i;
                return true;
            }
        }
        return true;
    }
}
