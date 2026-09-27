package io.lakio.krios.industry.client.screen;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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

    protected KriosTerminalScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** Feed status: negative = network fault, 0 = offline, positive = online KOIOS level. */
    protected abstract int terminalStatus();

    /** Feed X in panel space; machines shift it left of their "F" button. */
    protected int feedX() {
        return imageWidth - FEED_W - 4;
    }

    private boolean bootActive() {
        return !bootSkipped && Util.getMillis() - openedAt < BOOT_MILLIS;
    }

    /** Cyan title + the KOIOS feed. Call from renderLabels. */
    protected void renderTerminalHeader(GuiGraphics gui) {
        gui.drawString(font, title, titleLabelX, titleLabelY, COLOR_ACCENT, false);
        int status = terminalStatus();
        int x = feedX();
        int y = 3;
        int border = status < 0 ? COLOR_ERROR : status == 0 ? COLOR_TEXT_DIM : COLOR_ACCENT;
        gui.fill(x, y, x + FEED_W, y + FEED_H, 0xFF000000 | border);
        gui.fill(x + 1, y + 1, x + FEED_W - 1, y + FEED_H - 1, 0xFF10141B);
        // The head: two eyes on a blink cycle, a dim mouth; red when faulted.
        int eye = status < 0 ? COLOR_ERROR : status == 0 ? COLOR_ACCENT_DIM : COLOR_ACCENT;
        boolean blink = status > 0 && (Util.getMillis() / 100) % 26 == 0;
        int top = blink ? y + 6 : y + 4;
        gui.fill(x + 3, top, x + 5, y + 7, 0xFF000000 | eye);
        gui.fill(x + 9, top, x + 11, y + 7, 0xFF000000 | eye);
        gui.fill(x + 5, y + 9, x + 9, y + 10, 0xFF000000 | (status > 0 ? COLOR_ACCENT_DIM : COLOR_TEXT_DIM));
    }

    /** Feed tooltip + boot flicker. Call at the very end of render. */
    protected void renderTerminalOverlays(GuiGraphics gui, int mouseX, int mouseY) {
        int fx = leftPos + feedX();
        int fy = topPos + 3;
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
        if ((Util.getMillis() / 60) % 2 == 0) {
            gui.drawCenteredString(font, Component.translatable("gui.krios_industry.term.boot"),
                    leftPos + imageWidth / 2, topPos + imageHeight / 2 - 4, COLOR_ACCENT);
        }
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
