package io.lakio.krios.industry.client.screen;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * "KOIOS Archives" v1 (art direction): the in-game guide, rendered as an
 * overlay of the core terminal — KOIOS explaining its own ecosystem. One
 * screen per entry, a list on the left, wrapped text on the right. Entries
 * unlock and grow richer with the core's level (the guide's progression IS
 * the AI's progression). Client-only, all content in lang files.
 */
public class KoiosArchivesPanel {

    /** id → lang keys archive.<id>.title/.body(/.adv); minLevel gates it. */
    private record Entry(String id, int minLevel, boolean hasAdvanced) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("koios", 1, true),
            new Entry("diversity", 1, false),
            new Entry("generator", 1, false),
            new Entry("crusher", 1, false),
            new Entry("modules", 2, true),
            new Entry("network", 1, true),
            new Entry("conduit", 1, false),
            new Entry("calibrator", 1, false),
            new Entry("critical", 4, false));

    /** Level at which the advanced paragraphs appear. */
    private static final int ADVANCED_LEVEL = 3;

    private static final int W = 176;
    private static final int H = 166;
    private static final int LIST_X = 4;
    private static final int LIST_W = 60;
    private static final int LIST_Y = 18;
    private static final int LIST_STEP = 11;
    private static final int BODY_X = 68;
    private static final int BODY_W = 102;

    private boolean open;
    private int selected;

    public boolean isOpen() {
        return open;
    }

    public void toggleOpen() {
        open = !open;
    }

    public void render(GuiGraphics gui, Font font, int x, int y, int koiosLevel,
            int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        gui.fill(x, y, x + W, y + H, 0xF80D1016);
        gui.drawString(font, Component.translatable("gui.krios_industry.archive.title"),
                x + 5, y + 5, 0xFF31D8FF, false);
        // Close cross.
        gui.drawString(font, Component.literal("X"), x + W - 11, y + 5, 0xFF5A6170, false);
        gui.fill(x + 4, y + 15, x + W - 4, y + 16, 0xFF0A6E87);

        Component lockTip = null;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            int rowY = y + LIST_Y + i * LIST_STEP;
            boolean locked = koiosLevel < entry.minLevel();
            boolean hover = mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                    && mouseY >= rowY && mouseY < rowY + LIST_STEP;
            if (i == selected && !locked) {
                gui.fill(x + LIST_X, rowY, x + LIST_X + LIST_W, rowY + LIST_STEP - 1, 0xFF0A2A34);
            }
            int color = locked ? 0xFF31363F : i == selected ? 0xFF31D8FF
                    : hover ? 0xFFE8EAF0 : 0xFFA9AFBD;
            gui.drawString(font, Component.translatable(
                    "gui.krios_industry.archive." + entry.id() + ".title"),
                    x + LIST_X + 2, rowY + 1, color, false);
            if (locked && hover) {
                lockTip = Component.translatable("gui.krios_industry.archive.locked", entry.minLevel());
            }
        }

        Entry entry = ENTRIES.get(selected);
        int textY = y + LIST_Y;
        gui.drawString(font, Component.translatable(
                "gui.krios_industry.archive." + entry.id() + ".title")
                .withStyle(ChatFormatting.UNDERLINE), x + BODY_X, textY, 0xFFE8EAF0, false);
        textY += 13;
        Component body = Component.translatable("gui.krios_industry.archive." + entry.id() + ".body");
        gui.drawWordWrap(font, body, x + BODY_X, textY, BODY_W, 0xFFA9AFBD);
        textY += font.split(body, BODY_W).size() * 9 + 6;
        if (entry.hasAdvanced() && koiosLevel >= ADVANCED_LEVEL) {
            gui.drawWordWrap(font, Component.translatable(
                    "gui.krios_industry.archive." + entry.id() + ".adv")
                    .copy().withStyle(ChatFormatting.ITALIC), x + BODY_X, textY, BODY_W, 0xFF0A6E87);
        }
        if (lockTip != null) {
            gui.renderTooltip(font, lockTip, mouseX, mouseY);
        }
    }

    /** True when the click was handled (the overlay swallows all GUI clicks). */
    public boolean click(int x, int y, double mouseX, double mouseY, int koiosLevel) {
        if (!open) {
            return false;
        }
        if (mouseX >= x + W - 14 && mouseX < x + W - 2 && mouseY >= y + 3 && mouseY < y + 14) {
            open = false;
            return true;
        }
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            int rowY = y + LIST_Y + i * LIST_STEP;
            if (mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                    && mouseY >= rowY && mouseY < rowY + LIST_STEP
                    && koiosLevel >= entry.minLevel()) {
                selected = i;
                return true;
            }
        }
        return true;
    }
}
