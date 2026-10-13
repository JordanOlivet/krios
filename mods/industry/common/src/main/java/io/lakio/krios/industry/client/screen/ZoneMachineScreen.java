package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.client.ZoneHologramState;
import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Shared zone-machine terminal (ADR-025): annexes (module bay, the
 * machine's stats, faces), the in-world hologram eye toggle, the shared
 * voice states, and drawing helpers. Work areas belong to the machines
 * (the planting map and controls, the Lure's line gauges).
 */
public abstract class ZoneMachineScreen<T extends ZoneMachineMenu> extends MachineTerminalScreen<T> {

    protected static final int BTN = 10;
    protected static final int EYE_W = 12;

    private final HologramFacePanel facePanel = new HologramFacePanel(
            HologramFacePanel.columnsFor(ZoneMachineBlockEntity.SUPPORTED_FACE_BITS));

    private final ModuleBayPanel moduleBay = new ModuleBayPanel(
            () -> menu.activeModuleSlots(), menu::setModuleBayOpen);

    private AnnexPanel[] annexes;

    protected ZoneMachineScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** The machine's stats annex (built once, after construction). */
    protected abstract StatsPanel createStatsPanel();

    /** Lang segment of this machine's voice lines (voice.{key}.*). */
    protected abstract String voiceKey();

    /** Voice line for the machine's own idle states; null when idle. */
    protected abstract Component idlePhrase(int stateCode);

    protected int modulePoints(ModuleType type) {
        int points = 0;
        int first = menu.moduleSlotFirst();
        for (int i = first; i < first + ZoneMachineBlockEntity.MODULE_SLOT_COUNT; i++) {
            if (menu.getSlot(i).getItem().getItem() instanceof ModuleItem module
                    && module.getType() == type) {
                points += module.points();
            }
        }
        return points;
    }

    /** Machine-specific annexes, stacked between the stats and the faces. */
    protected AnnexPanel[] createExtraAnnexes() {
        return new AnnexPanel[0];
    }

    @Override
    protected AnnexPanel[] annexPanels() {
        if (annexes == null) {
            AnnexPanel[] extra = createExtraAnnexes();
            annexes = new AnnexPanel[3 + extra.length];
            annexes[0] = moduleBay;
            annexes[1] = createStatsPanel();
            System.arraycopy(extra, 0, annexes, 2, extra.length);
            annexes[annexes.length - 1] = facePanel;
        }
        return annexes;
    }

    @Override
    protected HologramFacePanel facePanel() {
        return facePanel;
    }

    @Override
    protected ModuleBayPanel moduleBay() {
        return moduleBay;
    }

    // --- Drawing helpers ---

    protected static void drawButton(GuiGraphics gui, int x, int y, int w, boolean enabled) {
        gui.fill(x, y, x + w, y + BTN, 0xFF0C0E13);
        gui.fill(x + 1, y + 1, x + w - 1, y + BTN - 1, enabled ? 0xFF262B35 : 0xFF1B202A);
    }

    /** Eye glyph: lit cyan while the hologram is pinned. */
    protected static void drawEye(GuiGraphics gui, int x, int y, boolean pinned) {
        drawButton(gui, x, y, EYE_W, true);
        int c = pinned ? 0xFF31D8FF : 0xFF5A6170;
        gui.fill(x + 4, y + 2, x + 8, y + 3, c);
        gui.fill(x + 2, y + 3, x + 4, y + 4, c);
        gui.fill(x + 8, y + 3, x + 10, y + 4, c);
        gui.fill(x + 2, y + 6, x + 4, y + 7, c);
        gui.fill(x + 8, y + 6, x + 10, y + 7, c);
        gui.fill(x + 4, y + 7, x + 8, y + 8, c);
        gui.fill(x + 1, y + 4, x + 2, y + 6, c);
        gui.fill(x + 10, y + 4, x + 11, y + 6, c);
        gui.fill(x + 5, y + 4, x + 7, y + 6, pinned ? 0xFF9FF0FF : c);
    }

    /** Amber warning sign: a triangle with an exclamation mark. */
    protected static void drawWarning(GuiGraphics gui, int x, int y) {
        int amber = 0xFF000000 | COLOR_ENERGY;
        for (int row = 0; row < 9; row++) {
            int half = row / 2;
            gui.fill(x + 5 - half, y + 1 + row, x + 6 + half, y + 2 + row, amber);
        }
        gui.fill(x + 5, y + 3, x + 6, y + 7, 0xFF10141B);
        gui.fill(x + 5, y + 8, x + 6, y + 9, 0xFF10141B);
    }

    protected boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + w
                && mouseY >= topPos + y && mouseY < topPos + y + h;
    }

    // --- Hologram eye (each machine places it) ---

    protected void renderEye(GuiGraphics gui, int x, int y) {
        drawEye(gui, leftPos + x, topPos + y, ZoneHologramState.isPinned(menu.machinePos()));
    }

    protected void renderEyeTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        boolean pinned = ZoneHologramState.isPinned(menu.machinePos());
        gui.renderComponentTooltip(font, List.of(
                Component.translatable(pinned ? "gui.krios_industry.zone.hologram_pinned"
                        : "gui.krios_industry.zone.hologram_show"),
                Component.translatable("gui.krios_industry.zone.hologram_calibrator")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
    }

    protected void toggleEye() {
        ZoneHologramState.toggle(menu.machinePos());
    }

    protected void clickButton(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    // --- Zone map and controls (resizable zones: planting machines, pods) ---

    protected static final int S = ZoneMachineMenu.MAP_SIZE;
    // Buttons sit slightly outside the map so a "27×27" label clears them.
    protected static final int MINUS_X = ZoneMachineMenu.MAP_X - 6;
    protected static final int PLUS_X = ZoneMachineMenu.MAP_X + S - 4;
    protected static final int EYE_X = PLUS_X + BTN + 6;

    /**
     * The zone map with -/+ one ring per click, the edge label and the
     * hologram eye (option A mockup, 2026-10-08).
     */
    protected void renderZoneControls(GuiGraphics gui) {
        drawMap(gui);
        int y = topPos + ZoneMachineMenu.CONTROLS_Y;
        int minusX = leftPos + MINUS_X;
        int plusX = leftPos + PLUS_X;
        boolean canShrink = menu.zoneEdge() > 1;
        boolean canGrow = menu.zoneEdge() < menu.zoneMaxEdge();
        drawButton(gui, minusX, y, BTN, canShrink);
        int glyph = canShrink ? 0xFFE8EAF0 : 0xFF5A6170;
        gui.fill(minusX + 2, y + 4, minusX + 8, y + 6, glyph);
        drawButton(gui, plusX, y, BTN, canGrow);
        glyph = canGrow ? 0xFFE8EAF0 : 0xFF5A6170;
        gui.fill(plusX + 2, y + 4, plusX + 8, y + 6, glyph);
        gui.fill(plusX + 4, y + 2, plusX + 6, y + 8, glyph);
        String label = menu.zoneEdge() + "×" + menu.zoneEdge();
        gui.drawCenteredString(font, label, leftPos + ZoneMachineMenu.MAP_X + S / 2, y + 1, 0xFFE8EAF0);
        renderEye(gui, EYE_X, ZoneMachineMenu.CONTROLS_Y);
    }

    /**
     * Holographic zone map: the frame is the effective tier's maximum, the
     * zone grows from the machine mark at the bottom edge (the zone starts
     * right in front of the machine and stays adjacent when shrunk).
     */
    private void drawMap(GuiGraphics gui) {
        int x = leftPos + ZoneMachineMenu.MAP_X;
        int y = topPos + ZoneMachineMenu.MAP_Y;
        gui.fill(x - 1, y - 1, x + S + 1, y + S + 1, 0xFF0C0E13);
        gui.fill(x, y, x + S, y + S, 0xFF12151B);
        // Tier maximum: dim frame.
        int dim = 0xFF000000 | COLOR_ACCENT_DIM;
        gui.fill(x, y, x + S, y + 1, dim);
        gui.fill(x, y + S - 1, x + S, y + S, dim);
        gui.fill(x, y, x + 1, y + S, dim);
        gui.fill(x + S - 1, y, x + S, y + S, dim);

        int max = menu.zoneMaxEdge();
        int edge = Math.min(menu.zoneEdge(), max);
        int px = Math.max(3, Math.round(edge * (float) S / max));
        int left = x + (S - px) / 2;
        int bottom = y + S;
        int top = bottom - px;
        gui.fill(left, top, left + px, bottom, 0x3031D8FF);
        // Cell grid when cells are big enough to read.
        float cell = px / (float) edge;
        if (cell >= 3) {
            for (int i = 1; i < edge; i++) {
                int off = Math.round(i * cell);
                gui.fill(left + off, top, left + off + 1, bottom, 0x3031D8FF);
                gui.fill(left, top + off, left + px, top + off + 1, 0x3031D8FF);
            }
        }
        int accent = 0xFF000000 | COLOR_ACCENT;
        gui.fill(left, top, left + px, top + 1, accent);
        gui.fill(left, bottom - 1, left + px, bottom, accent);
        gui.fill(left, top, left + 1, bottom, accent);
        gui.fill(left + px - 1, top, left + px, bottom, accent);
        // The machine, just below the map's bottom edge.
        gui.fill(x + S / 2 - 3, y + S + 2, x + S / 2 + 3, y + S + 4, 0xFF000000 | COLOR_ENERGY);
    }

    private boolean minusHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, MINUS_X, ZoneMachineMenu.CONTROLS_Y, BTN, BTN);
    }

    private boolean plusHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, PLUS_X, ZoneMachineMenu.CONTROLS_Y, BTN, BTN);
    }

    private boolean zoneEyeHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, EYE_X, ZoneMachineMenu.CONTROLS_Y, EYE_W, BTN);
    }

    private boolean mapHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, ZoneMachineMenu.MAP_X, ZoneMachineMenu.MAP_Y, S, S);
    }

    /** Tooltips of the zone controls; true when one was shown. */
    protected boolean renderZoneTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        if (minusHit(mouseX, mouseY)) {
            gui.renderTooltip(font, Component.translatable("gui.krios_industry.zone.shrink"), mouseX, mouseY);
        } else if (plusHit(mouseX, mouseY)) {
            gui.renderTooltip(font, Component.translatable("gui.krios_industry.zone.grow",
                    menu.zoneMaxEdge(), menu.zoneMaxEdge()), mouseX, mouseY);
        } else if (zoneEyeHit(mouseX, mouseY)) {
            renderEyeTooltip(gui, mouseX, mouseY);
        } else if (mapHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.krios_industry.zone.map",
                            menu.zoneEdge(), menu.zoneEdge(), menu.zoneMaxEdge(), menu.zoneMaxEdge()),
                    Component.translatable("gui.krios_industry.zone.map_front")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        } else {
            return false;
        }
        return true;
    }

    /** Clicks on the zone controls; true when consumed. */
    protected boolean clickZoneControls(double mouseX, double mouseY) {
        if (minusHit(mouseX, mouseY) && menu.zoneEdge() > 1) {
            clickButton(ZoneMachineBlockEntity.BUTTON_ZONE_SHRINK);
            return true;
        }
        if (plusHit(mouseX, mouseY) && menu.zoneEdge() < menu.zoneMaxEdge()) {
            clickButton(ZoneMachineBlockEntity.BUTTON_ZONE_GROW);
            return true;
        }
        if (zoneEyeHit(mouseX, mouseY)) {
            toggleEye();
            return true;
        }
        return false;
    }

    // --- Voice ---

    @Override
    protected Component workingPhrase() {
        String base = "gui.krios_industry.voice." + voiceKey() + ".";
        return switch (menu.stateCode()) {
            case ZoneMachineBlockEntity.STATE_WORKING -> Component.translatable(base + "working");
            case ZoneMachineBlockEntity.STATE_BUFFER_FULL -> Component.translatable(base + "buffer_full");
            default -> idlePhrase(menu.stateCode());
        };
    }
}
