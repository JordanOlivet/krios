package io.lakio.krios.industry.client.screen;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Shared diegetic "KOIOS terminal" chrome (art direction): cyan title, the
 * AI's little video feed with the network status as border color, and a
 * short skippable boot flicker on open. Subclasses draw their own panel and
 * call {@link #renderTerminalHeader} from renderLabels (translated space)
 * and {@link #renderTerminalOverlays} at the very end of render.
 */
public abstract class KriosTerminalScreen<T extends AbstractContainerMenu>
        extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<T> {

    // Palette A.
    public static final int COLOR_TEXT = 0xE8EAF0;
    public static final int COLOR_TEXT_DIM = 0x5A6170;
    public static final int COLOR_ACCENT = 0x31D8FF;
    public static final int COLOR_ACCENT_DIM = 0x0A6E87;
    public static final int COLOR_ENERGY = 0xFFB03A;
    public static final int COLOR_ERROR = 0xE5484D;

    private static final int BOOT_MILLIS = 220;
    private static final int FEED_W = 14;
    private static final int FEED_H = 12;

    private final long openedAt = Util.getMillis();
    private boolean bootSkipped;
    // KOIOS "life": the feed's eyes follow the cursor and get bored when
    // nothing moves for a while.
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private long lastActivity = Util.getMillis();

    protected KriosTerminalScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** Feed status: negative = network fault, 0 = offline, positive = online KOIOS level. */
    protected abstract int terminalStatus();

    /** Standard header order: close cross rightmost, feed to its left. */
    protected int feedX() {
        return imageWidth - 34;
    }

    /** Codex-style framed panel (shared visual language). Call from renderBg. */
    protected void renderCodexFrame(GuiGraphics gui) {
        int x = leftPos;
        int y = topPos;
        gui.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF0A6E87);
        gui.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, 0xFF0C0E13);
        gui.fill(x, y, x + imageWidth, y + imageHeight, 0xFF10141B);
        gui.fill(x, y, x + imageWidth, y + 18, 0xFF1B202A);
        gui.fill(x, y + 18, x + imageWidth, y + 19, 0xFF0A6E87);
    }

    /** Close cross, rightmost (standard header). Call from renderLabels. */
    protected void renderCloseCross(GuiGraphics gui, int mouseX, int mouseY) {
        boolean hover = isCloseCrossHit(mouseX, mouseY);
        gui.fill(imageWidth - 16, 4, imageWidth - 4, 16, hover ? 0xFF6B2F2F : 0xFF262B35);
        gui.drawCenteredString(font, "X", imageWidth - 10, 6, 0xFFE8EAF0);
    }

    /** Absolute-space hit test for the standard close cross. */
    protected boolean isCloseCrossHit(double mouseX, double mouseY) {
        return mouseX >= leftPos + imageWidth - 16 && mouseX < leftPos + imageWidth - 4
                && mouseY >= topPos + 4 && mouseY < topPos + 16;
    }

    /** Terminal voice line "> phrase_", blinking cursor. Translated space. */
    protected void drawVoiceLine(GuiGraphics gui, Component phrase, int x, int y, int width) {
        String cursor = (Util.getMillis() / 500) % 2 == 0 ? "_" : "";
        Component line = Component.literal("> ").append(phrase).append(Component.literal(cursor));
        gui.drawWordWrap(font, line, x, y, width, 0xFF5FC8DC);
    }

    private boolean bootActive() {
        return !bootSkipped && io.lakio.krios.industry.client.ClientPrefs.bootAnimation()
                && Util.getMillis() - openedAt < BOOT_MILLIS;
    }

    /** Cyan title + the KOIOS feed. Call from renderLabels (translated space). */
    protected void renderTerminalHeader(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(font, title, titleLabelX, titleLabelY, COLOR_ACCENT, false);
        int status = terminalStatus();
        int x = feedX();
        // Aligned with the machines' 12px "F" button row (y 4..16).
        int y = 4;
        int border = status < 0 ? COLOR_ERROR : status == 0 ? COLOR_TEXT_DIM : COLOR_ACCENT;
        gui.fill(x, y, x + FEED_W, y + FEED_H, 0xFF000000 | border);
        gui.fill(x + 1, y + 1, x + FEED_W - 1, y + FEED_H - 1, 0xFF10141B);

        // Life: track cursor movement to know when the player idles.
        long now = Util.getMillis();
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            lastActivity = now;
        }
        boolean bored = status > 0 && now - lastActivity > 30_000;

        // Eyes follow the cursor by a pixel; droop when bored; red on fault.
        int eye = status < 0 ? COLOR_ERROR : status == 0 ? COLOR_ACCENT_DIM : COLOR_ACCENT;
        int feedCx = leftPos + x + FEED_W / 2;
        int feedCy = topPos + y + FEED_H / 2;
        int dx = status > 0 && !bored ? Mth.clamp((mouseX - feedCx) / 30, -1, 1) : 0;
        int dy = status > 0 && !bored ? Mth.clamp((mouseY - feedCy) / 30, -1, 1) : 0;
        boolean blink = status > 0 && (now / 100) % (bored ? 40 : 26) == 0;
        int top = y + 4 + (blink ? 2 : bored ? 1 : 0) + dy;
        gui.fill(x + 3 + dx, top, x + 5 + dx, y + 7 + dy, 0xFF000000 | eye);
        gui.fill(x + 9 + dx, top, x + 11 + dx, y + 7 + dy, 0xFF000000 | eye);
        // Mouth: flat normally; bored = corners drop BELOW the line (frown).
        gui.fill(x + 5, y + 9, x + 9, y + 10, 0xFF000000 | (status > 0 ? COLOR_ACCENT_DIM : COLOR_TEXT_DIM));
        if (bored) {
            gui.fill(x + 4, y + 10, x + 5, y + 11, 0xFF000000 | COLOR_ACCENT_DIM);
            gui.fill(x + 9, y + 10, x + 10, y + 11, 0xFF000000 | COLOR_ACCENT_DIM);
        }
    }

    /** Feed tooltip + boot flicker. Call at the very end of render. */
    protected void renderTerminalOverlays(GuiGraphics gui, int mouseX, int mouseY) {
        int fx = leftPos + feedX();
        int fy = topPos + 4;
        if (!bootActive() && mouseX >= fx && mouseX < fx + FEED_W && mouseY >= fy && mouseY < fy + FEED_H) {
            gui.renderTooltip(font, statusLine(), mouseX, mouseY);
        }
        if (!bootActive()) {
            return;
        }
        float progress = (Util.getMillis() - openedAt) / (float) BOOT_MILLIS;
        int alpha = (int) ((1 - progress) * 210);
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, (alpha << 24) | 0x10141B);
        int sweepY = topPos + (int) (progress * (imageHeight - 2)) + 1;
        gui.fill(leftPos + 2, sweepY, leftPos + imageWidth - 2, sweepY + 1, 0x9031D8FF);
    }

    /**
     * Stateless feed box (blink + cursor tracking, no bored state) for
     * overlays that cannot host the full chrome (e.g. the Codex panel).
     * Coordinates are absolute screen space.
     */
    public static void drawFeedBox(GuiGraphics gui, int x, int y, int status, int mouseX, int mouseY) {
        int border = status < 0 ? COLOR_ERROR : status == 0 ? COLOR_TEXT_DIM : COLOR_ACCENT;
        gui.fill(x, y, x + FEED_W, y + FEED_H, 0xFF000000 | border);
        gui.fill(x + 1, y + 1, x + FEED_W - 1, y + FEED_H - 1, 0xFF10141B);
        int eye = status < 0 ? COLOR_ERROR : status == 0 ? COLOR_ACCENT_DIM : COLOR_ACCENT;
        int dx = status > 0 ? Mth.clamp((mouseX - (x + FEED_W / 2)) / 30, -1, 1) : 0;
        int dy = status > 0 ? Mth.clamp((mouseY - (y + FEED_H / 2)) / 30, -1, 1) : 0;
        boolean blink = status > 0 && (Util.getMillis() / 100) % 26 == 0;
        int top = y + 4 + (blink ? 2 : 0) + dy;
        gui.fill(x + 3 + dx, top, x + 5 + dx, y + 7 + dy, 0xFF000000 | eye);
        gui.fill(x + 9 + dx, top, x + 11 + dx, y + 7 + dy, 0xFF000000 | eye);
        gui.fill(x + 5, y + 9, x + 9, y + 10, 0xFF000000 | (status > 0 ? COLOR_ACCENT_DIM : COLOR_TEXT_DIM));
    }

    private Component statusLine() {
        int status = terminalStatus();
        if (status < 0) {
            return Component.translatable("gui.krios_industry.diag.status.fault");
        }
        if (status == 0) {
            return Component.translatable("gui.krios_industry.diag.status.orphan");
        }
        return Component.translatable("gui.krios_industry.diag.status.online", status);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bootActive()) {
            bootSkipped = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
