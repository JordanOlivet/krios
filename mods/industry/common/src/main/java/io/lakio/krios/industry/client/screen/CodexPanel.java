package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
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
            new Entry("progression", 1, false),
            new Entry("diversity", 1, false),
            new Entry("thermal_reactor", 1, false),
            new Entry("photon_array", 1, false),
            new Entry("fractal_grinder", 1, false),
            new Entry("plasma_forge", 1, false),
            new Entry("chrono_farmer", 1, false),
            new Entry("cyclic_forester", 1, false),
            new Entry("quantum_lure", 1, false),
            new Entry("breeding_pod", 1, false),
            new Entry("harvest_pod", 1, false),
            new Entry("phase_death_field", 1, false),
            new Entry("bio_synthesizer", 1, false),
            new Entry("modules", 2, true),
            new Entry("network", 1, true),
            new Entry("conduit", 1, false),
            new Entry("calibrator", 1, false),
            new Entry("critical", 4, false));

    /** Level at which the advanced paragraphs appear. */
    private static final int ADVANCED_LEVEL = 3;

    // Sized for the Progression page's tallest state (current ladder +
    // projections + corrupted lines) and for the corrupted lines' width;
    // still fits the screen at GUI scale 4 (480x270 units).
    private static final int W = 320;
    private static final int H = 246;
    private static final int HEADER_H = 18;
    private static final int SIDEBAR_X = 6;
    private static final int SIDEBAR_W = 104;
    private static final int ROW_H = 16;
    /** Sidebar viewport: from the first row to the panel's bottom margin. */
    private static final int LIST_TOP = HEADER_H + 6;
    private static final int LIST_H = H - LIST_TOP - 6;
    private static final int PAGE_X = 118;
    private static final int PAGE_W = W - PAGE_X - 10;

    private boolean open;
    private int selected;
    /** Sidebar scroll, in pixels (wheel steps by one row). */
    private int scroll;

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

    public void render(GuiGraphics gui, Font font, int screenW, int screenH, KoiosCoreMenu menu,
            int feedStatus, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        int koiosLevel = menu.getKoiosLevel();
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

        // Sidebar: the section list, scrolled and clipped to its viewport
        // once the entries outgrow it; the divider doubles as the scrollbar.
        int dividerX = x + SIDEBAR_X + SIDEBAR_W + 3;
        gui.fill(dividerX, y + HEADER_H + 4, dividerX + 1, y + H - 6, 0xFF262B35);
        if (maxScroll() > 0) {
            int trackH = H - 6 - (HEADER_H + 4);
            int thumbH = Math.max(12, trackH * LIST_H / listHeight());
            int thumbY = y + HEADER_H + 4 + (trackH - thumbH) * scroll / maxScroll();
            gui.fill(dividerX - 1, thumbY, dividerX + 2, thumbY + thumbH, 0xFF0A6E87);
        }
        gui.enableScissor(x + SIDEBAR_X, y + LIST_TOP, x + SIDEBAR_X + SIDEBAR_W, y + LIST_TOP + LIST_H);
        boolean inList = inList(x, y, mouseX, mouseY);
        Component tooltip = null;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            int rowY = y + LIST_TOP + i * ROW_H - scroll;
            if (rowY + ROW_H <= y + LIST_TOP || rowY >= y + LIST_TOP + LIST_H) {
                continue;
            }
            boolean locked = koiosLevel < entry.minLevel();
            boolean hover = inList && mouseX >= x + SIDEBAR_X && mouseX < x + SIDEBAR_X + SIDEBAR_W
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
        gui.disableScissor();

        // The page.
        Entry entry = ENTRIES.get(selected);
        int textX = x + PAGE_X;
        int textY = y + HEADER_H + 8;
        gui.drawString(font, Component.translatable(
                "gui.krios_industry.codex." + entry.id() + ".title"),
                textX, textY, 0xFFE8EAF0, false);
        gui.fill(textX, textY + 11, textX + PAGE_W, textY + 12, 0xFF0A6E87);
        textY += 18;
        if ("progression".equals(entry.id())) {
            renderProgression(gui, font, textX, textY, menu);
        } else {
            String baseKey = "gui.krios_industry.codex." + entry.id();
            // Mastered tiers can rewrite an entry's body: the highest
            // .body.t<N> at or under the core's tier wins (fallback .body).
            String bodyKey = baseKey + ".body";
            for (int t = menu.getTier(); t >= 2; t--) {
                if (I18n.exists(bodyKey + ".t" + t)) {
                    bodyKey = bodyKey + ".t" + t;
                    break;
                }
            }
            Component body = Component.translatable(bodyKey);
            gui.drawWordWrap(font, body, textX, textY, PAGE_W, 0xFFA9AFBD);
            textY += font.split(body, PAGE_W).size() * 9 + 10;
            if (entry.hasAdvanced() && koiosLevel >= ADVANCED_LEVEL) {
                Component adv = Component.translatable(baseKey + ".adv")
                        .copy().withStyle(ChatFormatting.ITALIC);
                int advHeight = font.split(adv, PAGE_W - 6).size() * 9;
                gui.fill(textX, textY, textX + 1, textY + advHeight, 0xFF0A6E87);
                gui.drawWordWrap(font, adv, textX + 6, textY, PAGE_W - 6, 0xFF5FA8BC);
                textY += advHeight + 10;
            }
            // Contextual projection (three-band visibility): while KOIOS
            // dreams near the cap, the entry carries one amber line about
            // this subject's next form; it vanishes once the tier is real.
            String projectionKey = baseKey + ".projection.t" + (menu.getTier() + 1);
            if (projectionsVisible(menu) && I18n.exists(projectionKey)) {
                Component projection = Component.translatable(projectionKey);
                int height = font.split(projection, PAGE_W - 6).size() * 9;
                gui.fill(textX, textY, textX + 1, textY + height, 0xFF7A5A1C);
                gui.drawWordWrap(font, projection, textX + 6, textY, PAGE_W - 6, 0xFFB08848);
            }
        }
        if (tooltip != null) {
            gui.renderTooltip(font, tooltip, mouseX, mouseY);
        }
        gui.pose().popPose();
    }

    /**
     * The three-band tier ladder (design 2026-09-30/10-05): past tiers
     * mastered, the current tier's rung list, the NEXT tier as amber
     * Projections once XP nears the cap (KOIOS extrapolates — conditional
     * phrasing), everything further as corrupted lines. All text in lang;
     * no live per-rung state — that lives on the core screen's UNLOCKS.
     */
    private void renderProgression(GuiGraphics gui, Font font, int x, int y, KoiosCoreMenu menu) {
        int tier = menu.getTier();
        Component intro = Component.translatable("gui.krios_industry.codex.progression.body");
        gui.drawWordWrap(font, intro, x, y, PAGE_W, 0xFFA9AFBD);
        y += font.split(intro, PAGE_W).size() * 9 + 8;
        for (int t = 1; t <= KoiosBalance.MAX_TIER; t++) {
            // Mastered and current tiers carry their diegetic name; future
            // tiers stay nameless — KOIOS has not modeled those forms yet.
            Component tierName = Component.translatable("gui.krios_industry.koios.tier.name." + t);
            if (t < tier) {
                y = drawBand(gui, font, x, y,
                        Component.translatable("gui.krios_industry.codex.progression.mastered", t, tierName),
                        0xFF31D8FF, "gui.krios_industry.codex.progression.t" + t + ".past", 0xFFA9AFBD);
            } else if (t == tier) {
                y = drawBand(gui, font, x, y,
                        Component.translatable("gui.krios_industry.codex.progression.current", t, tierName),
                        0xFFE8EAF0, "gui.krios_industry.codex.progression.t" + t + ".ladder", 0xFFA9AFBD);
            } else if (t == tier + 1 && projectionsVisible(menu)) {
                y = drawBand(gui, font, x, y,
                        Component.translatable("gui.krios_industry.codex.progression.projections", t),
                        0xFFFFB03A, "gui.krios_industry.codex.progression.projection.t" + t, 0xFFB08848);
                Component needs = Component.translatable(
                        "gui.krios_industry.codex.progression.projection.needs",
                        KoiosBalance.criticalLevel(tier));
                gui.drawWordWrap(font, needs, x + 6, y, PAGE_W - 6, 0xFF8A6A38);
                y += font.split(needs, PAGE_W - 6).size() * 9 + 6;
            } else {
                // Corrupted band (art direction): a readable label framed by
                // short obfuscated runs — the ladder has rungs up here, and
                // that is all the band gives away.
                Component corrupted = Component.literal("")
                        .append(Component.translatable("gui.krios_industry.codex.progression.tier", t))
                        .append(Component.literal(" — "))
                        .append(Component.literal("██").withStyle(ChatFormatting.OBFUSCATED))
                        .append(Component.literal(" "))
                        .append(Component.translatable("gui.krios_industry.codex.progression.corrupted"))
                        .append(Component.literal(" "))
                        .append(Component.literal("█").withStyle(ChatFormatting.OBFUSCATED));
                gui.drawString(font, corrupted, x, y, 0xFF4A5160, false);
                y += 13;
            }
        }
    }

    /** Band header + lang body (skipped when the key is not translated). */
    private int drawBand(GuiGraphics gui, Font font, int x, int y, Component header,
            int headerColor, String bodyKey, int bodyColor) {
        gui.drawString(font, header, x, y, headerColor, false);
        y += 11;
        if (I18n.exists(bodyKey)) {
            Component body = Component.translatable(bodyKey);
            gui.drawWordWrap(font, body, x + 6, y, PAGE_W - 6, bodyColor);
            y += font.split(body, PAGE_W - 6).size() * 9;
        }
        return y + 6;
    }

    /** Next-tier projections: XP near the cap AND the content is written. */
    private static boolean projectionsVisible(KoiosCoreMenu menu) {
        return KoiosBalance.projectionsActive(menu.getXp(), menu.getTier())
                && I18n.exists("gui.krios_industry.codex.progression.projection.t"
                        + (menu.getTier() + 1));
    }

    /**
     * Badge signature: how many codex entries this state can open, plus one
     * while projections are live. The core stores the signature it was last
     * opened at; the header book shows a dot while this outgrows it.
     */
    public static int visibleSignature(KoiosCoreMenu menu) {
        int count = 0;
        for (Entry entry : ENTRIES) {
            if (menu.getKoiosLevel() >= entry.minLevel()) {
                count++;
            }
        }
        return count + (projectionsVisible(menu) ? 1 : 0);
    }

    /** True when the click was handled (the overlay swallows every click). */
    public boolean click(int screenW, int screenH, double mouseX, double mouseY, KoiosCoreMenu menu) {
        if (!open) {
            return false;
        }
        int koiosLevel = menu.getKoiosLevel();
        int x = panelX(screenW);
        int y = panelY(screenH);
        if (mouseX >= x + W - 16 && mouseX < x + W - 4 && mouseY >= y + 3 && mouseY < y + 15) {
            open = false;
            return true;
        }
        if (!inList(x, y, mouseX, mouseY)) {
            return true;
        }
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            int rowY = y + LIST_TOP + i * ROW_H - scroll;
            if (mouseX >= x + SIDEBAR_X && mouseX < x + SIDEBAR_X + SIDEBAR_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H
                    && koiosLevel >= entry.minLevel()) {
                selected = i;
                return true;
            }
        }
        return true;
    }

    /** Mouse wheel over the sidebar scrolls it one row per notch. */
    public boolean scroll(int screenW, int screenH, double mouseX, double mouseY, double delta) {
        if (!open) {
            return false;
        }
        if (inList(panelX(screenW), panelY(screenH), mouseX, mouseY) && delta != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * ROW_H));
        }
        return true;
    }

    private static boolean inList(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x + SIDEBAR_X && mouseX < x + SIDEBAR_X + SIDEBAR_W
                && mouseY >= y + LIST_TOP && mouseY < y + LIST_TOP + LIST_H;
    }

    private static int listHeight() {
        return ENTRIES.size() * ROW_H;
    }

    private static int maxScroll() {
        return Math.max(0, listHeight() - LIST_H);
    }
}
