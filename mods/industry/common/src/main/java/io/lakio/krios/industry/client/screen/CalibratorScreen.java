package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.tool.CalibratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Network diagnostic readout (display-only; the future core network
 * dashboard shares this style).
 */
public class CalibratorScreen extends KriosTerminalScreen<CalibratorMenu> {

    private static final int LINE_HEIGHT = 12;

    public CalibratorScreen(CalibratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 180;
    }

    @Override
    protected int terminalStatus() {
        return switch (menu.status()) {
            case CalibratorMenu.STATUS_ONLINE -> Math.max(1, menu.controllerLevel());
            case CalibratorMenu.STATUS_FAULT -> -1;
            default -> 0;
        };
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTerminalOverlays(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Codex-style framed panel (same language as the Archives).
        guiGraphics.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF0A6E87);
        guiGraphics.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, 0xFF0C0E13);
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF10141B);
        guiGraphics.fill(x, y, x + imageWidth, y + 18, 0xFF1B202A);
        guiGraphics.fill(x, y + 18, x + imageWidth, y + 19, 0xFF0A6E87);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTerminalHeader(guiGraphics, mouseX, mouseY);
        int y = 25;
        int status = menu.status();
        Component statusLine = switch (status) {
            case CalibratorMenu.STATUS_ONLINE -> Component.translatable(
                    "gui.krios_industry.diag.status.online", menu.controllerLevel());
            case CalibratorMenu.STATUS_ORPHAN -> Component.translatable(
                    "gui.krios_industry.diag.status.orphan");
            case CalibratorMenu.STATUS_FAULT -> Component.translatable(
                    "gui.krios_industry.diag.status.fault");
            default -> Component.translatable("gui.krios_industry.diag.status.none");
        };
        int statusColor = switch (status) {
            case CalibratorMenu.STATUS_ONLINE -> 0xFF31D8FF;
            case CalibratorMenu.STATUS_FAULT -> 0xFFE5484D;
            default -> 0xFFFFB03A;
        };
        guiGraphics.drawString(font, statusLine, 8, y, statusColor, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.nodes",
                menu.machineCount(), menu.blockCount()), 8, y, 0xFFA9AFBD, false);
        y += LINE_HEIGHT + 2;

        // Energy section: divider, caption + pool numbers, gauge, then
        // SHORT stat lines (production/consumed side by side, demand below —
        // no more overflowing combined line).
        guiGraphics.fill(8, y, imageWidth - 8, y + 1, 0xFF262B35);
        y += 6;
        guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.section.energy"),
                8, y, 0xFF5A6170, false);
        Component pool = Component.translatable("gui.krios_industry.diag.energy",
                menu.energyStored(), menu.energyCapacity());
        guiGraphics.drawString(font, pool, imageWidth - 8 - font.width(pool), y, 0xFFE8EAF0, false);
        y += 11;
        int gaugeW = imageWidth - 16;
        guiGraphics.fill(8, y, 8 + gaugeW, y + 8, 0xFF0C0E13);
        guiGraphics.fill(9, y + 1, 7 + gaugeW, y + 7, 0xFF12151B);
        long capacity = menu.energyCapacity();
        if (capacity > 0) {
            int fill = (int) ((gaugeW - 2) * Math.min(1D, (double) menu.energyStored() / capacity));
            if (fill > 0) {
                guiGraphics.fill(9, y + 1, 9 + fill, y + 7, 0xFFFFB03A);
            }
        }
        y += 13;
        int supply = menu.supplyPerTick();
        int draw = menu.drawPerTick();
        guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.energy_production",
                supply), 8, y, 0xFF9AD9E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.energy_consumed",
                menu.consumedPerTick()), imageWidth / 2 + 4, y, 0xFFE8EAF0, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.energy_demand",
                draw), 8, y, draw > supply ? 0xFFFFB03A : 0xFFA9AFBD, false);
        y += LINE_HEIGHT + 4;

        // Alerts block.
        if (menu.starvedCount() > 0) {
            guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.energy_starved",
                    menu.starvedCount()), 8, y, 0xFFE5484D, false);
            y += LINE_HEIGHT;
        }
        if (capacity > 0 && menu.energyStored() == 0) {
            guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.energy_empty"),
                    8, y, 0xFFE5484D, false);
            y += LINE_HEIGHT;
        } else if (capacity > 0 && menu.energyStored() * 10 < capacity) {
            guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.energy_low"),
                    8, y, 0xFFFFB03A, false);
            y += LINE_HEIGHT;
        }
        if (menu.hintMembers() > 0) {
            guiGraphics.drawString(font, Component.translatable("gui.krios_industry.diag.hints",
                    menu.hintMembers()), 8, y, 0xFFFFB03A, false);
        }
    }
}
