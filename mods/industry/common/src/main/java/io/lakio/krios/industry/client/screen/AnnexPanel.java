package io.lakio.krios.industry.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Codex-style annex terminal chassis (art direction): a satellite panel
 * docked beside a main terminal. Open, it is a mini framed terminal whose
 * title bar collapses it on click; closed, it shrinks to a small tab
 * carrying the panel's pixel icon (click to reopen, title as tooltip).
 * Subclasses provide content, tab icon and the persistence hook. No toggle
 * button lives in the main frame — the control sits on the panel itself.
 *
 * <p>Panels on one side form a vertical STACK (user call 2026-10-08, the
 * front-end "stack panel" idea): {@link #stack} lays them out in order from
 * the main frame's top border, each one a fixed gap below the previous
 * one's actual footprint (open frame or collapsed tab), so spacing holds
 * whatever the neighbors and their states. One constraint: vanilla slot
 * coordinates are final, so a panel hosting real slots
 * ({@link #anchorsSlots()}) can never move — it must come first on its
 * side, where its position is constant.
 */
public abstract class AnnexPanel {

    public enum Side { LEFT, RIGHT }

    public static final int TAB_SIZE = 14;
    /** Title bar height; content starts right below. */
    public static final int TITLE_H = 15;
    protected static final int PAD = 4;
    /** Breathing room between the main frame edge and the annex. */
    private static final int GAP = 8;
    /**
     * Stack origin, relative to topPos: the panel's 1px border then lines
     * up with the main frame's outer border (topPos - 2).
     */
    public static final int STACK_TOP = -1;
    /** Vertical gap between two stacked panels' borders. */
    private static final int STACK_GAP = 6;

    private final Side side;
    /** Offset from topPos, assigned by {@link #stack}. */
    private int stackY = STACK_TOP;
    private final String titleKey;
    private boolean open;
    // The title defines the panel's minimum footprint (narrow content must
    // not let the title bleed over the main frame). Refreshed each render;
    // clicks always land after one.
    private int titleMinContent;

    protected AnnexPanel(Side side, String titleKey, boolean initiallyOpen) {
        this.side = side;
        this.titleKey = titleKey;
        this.open = initiallyOpen;
    }

    public boolean isOpen() {
        return open;
    }

    /** True for panels hosting real menu slots: they must head their stack. */
    protected boolean anchorsSlots() {
        return false;
    }

    /**
     * Lays out every side's stack in array order. Call before any render or
     * hit test of the frame (open states may have changed since).
     */
    public static void stack(AnnexPanel... panels) {
        for (Side side : Side.values()) {
            int y = STACK_TOP;
            boolean first = true;
            for (AnnexPanel panel : panels) {
                if (panel.side != side) {
                    continue;
                }
                if (panel.anchorsSlots() && !first) {
                    throw new IllegalStateException("A slot-hosting annex must head its stack");
                }
                first = false;
                panel.stackY = y;
                // Footprint includes the 1px border above and below.
                y += (panel.open ? panel.panelHeight() : TAB_SIZE) + 2 + STACK_GAP;
            }
        }
    }

    /** Inner content size, borders and title bar excluded. */
    protected abstract int contentWidth();

    protected abstract int contentHeight();

    protected abstract void renderContent(GuiGraphics gui, Font font, int x, int y,
            int mouseX, int mouseY);

    /** Pixel icon on the collapsed tab, drawn in an 8x8 box at (x, y). */
    protected abstract void drawTabIcon(GuiGraphics gui, int x, int y);

    /** Persistence hook, called after every toggle. */
    protected void onToggle(boolean open) {
    }

    /** Pre-render hook for dynamic content sizing (fonts are render-only). */
    protected void layout(Font font) {
    }

    public int panelWidth() {
        return Math.max(contentWidth(), titleMinContent) + PAD * 2;
    }

    /** Content width the panel actually grants (title can widen it). */
    protected int effectiveContentWidth() {
        return Math.max(contentWidth(), titleMinContent);
    }

    public int panelHeight() {
        return TITLE_H + contentHeight() + PAD;
    }

    public int panelX(int leftPos, int imageWidth) {
        return side == Side.LEFT ? leftPos - panelWidth() - GAP : leftPos + imageWidth + GAP;
    }

    public int panelY(int topPos) {
        return topPos + stackY;
    }

    public int contentX(int leftPos, int imageWidth) {
        return panelX(leftPos, imageWidth) + PAD;
    }

    public int contentY(int topPos) {
        return panelY(topPos) + TITLE_H;
    }

    private int tabX(int leftPos, int imageWidth) {
        return side == Side.LEFT ? leftPos - TAB_SIZE - GAP : leftPos + imageWidth + GAP;
    }

    public void render(GuiGraphics gui, Font font, int leftPos, int topPos, int imageWidth,
            int mouseX, int mouseY) {
        layout(font);
        // Title + collapse glyph clearance.
        titleMinContent = font.width(Component.translatable(titleKey)) + 10;
        int y = panelY(topPos);
        if (!open) {
            int x = tabX(leftPos, imageWidth);
            boolean hover = mouseX >= x && mouseX < x + TAB_SIZE
                    && mouseY >= y && mouseY < y + TAB_SIZE;
            gui.fill(x - 1, y - 1, x + TAB_SIZE + 1, y + TAB_SIZE + 1, 0xFF0A6E87);
            gui.fill(x, y, x + TAB_SIZE, y + TAB_SIZE, hover ? 0xFF1B202A : 0xFF10141B);
            drawTabIcon(gui, x + 3, y + 3);
            return;
        }
        int x = panelX(leftPos, imageWidth);
        int w = panelWidth();
        int h = panelHeight();
        boolean titleHover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + TITLE_H;
        // Mini codex frame: accent border, dark body, clickable title bar.
        gui.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0A6E87);
        gui.fill(x, y, x + w, y + h, 0xFF10141B);
        gui.fill(x, y, x + w, y + TITLE_H - 1, titleHover ? 0xFF262B35 : 0xFF1B202A);
        gui.fill(x, y + TITLE_H - 1, x + w, y + TITLE_H, 0xFF0A6E87);
        gui.drawString(font, Component.translatable(titleKey), x + PAD, y + 4, 0xFF31D8FF, false);
        // Collapse hint, right end of the title bar.
        gui.fill(x + w - 9, y + 7, x + w - 4, y + 8, 0xFF5A6170);
        renderContent(gui, font, x + PAD, y + TITLE_H, mouseX, mouseY);
    }

    /** Collapsed-tab tooltip; call late so it draws above everything. */
    public void renderTabTooltip(GuiGraphics gui, Font font, int leftPos, int topPos,
            int imageWidth, int mouseX, int mouseY) {
        if (open) {
            return;
        }
        int x = tabX(leftPos, imageWidth);
        int y = panelY(topPos);
        if (mouseX >= x && mouseX < x + TAB_SIZE && mouseY >= y && mouseY < y + TAB_SIZE) {
            gui.renderTooltip(font, Component.translatable(titleKey), mouseX, mouseY);
        }
    }

    /** Tab or title-bar click toggles the panel. True when consumed. */
    public boolean chassisClick(int leftPos, int topPos, int imageWidth,
            double mouseX, double mouseY) {
        int y = panelY(topPos);
        if (!open) {
            int x = tabX(leftPos, imageWidth);
            if (mouseX >= x && mouseX < x + TAB_SIZE && mouseY >= y && mouseY < y + TAB_SIZE) {
                open = true;
                onToggle(true);
                return true;
            }
            return false;
        }
        int x = panelX(leftPos, imageWidth);
        if (mouseX >= x && mouseX < x + panelWidth() && mouseY >= y && mouseY < y + TITLE_H) {
            open = false;
            onToggle(false);
            return true;
        }
        return false;
    }
}
