package io.lakio.krios.industry.client.screen;

import java.util.List;

import io.lakio.krios.industry.client.ZoneHologramState;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Shared zone-machine terminal (option A mockup, 2026-10-08 — Chrono
 * Farmer, Cyclic Forester...): plant column, a
 * holographic zone map (outer frame = the tier maximum, cyan square = the
 * current zone, amber mark = the machine, so the zone grows "upward" from
 * it — away from the player standing at the screen), -/+ one ring per click, and the eye toggle pinning the in-world
 * zone hologram (client-side). 3x3 harvest buffer on the right.
 */
public abstract class ZoneMachineScreen<T extends ZoneMachineMenu> extends MachineTerminalScreen<T> {

    private static final int S = ZoneMachineMenu.MAP_SIZE;
    private static final int BTN = 10;
    // Buttons sit slightly outside the map so a "27×27" label clears them.
    private static final int MINUS_X = ZoneMachineMenu.MAP_X - 6;
    private static final int PLUS_X = ZoneMachineMenu.MAP_X + S - 4;
    private static final int EYE_X = PLUS_X + BTN + 6;
    // Seed-voiding toggle in the free column left of the buffer (the
    // buffer's last row reaches y=80, no room below it), aligned with its
    // top; the warning sign sits right under the toggle.
    private static final int VOID_W = 12;
    private static final int VOID_X = ZoneMachineMenu.BUFFER_X - 1 - 4 - VOID_W;
    private static final int VOID_Y = ZoneMachineMenu.SLOT_TOP_Y - 1;
    private static final int WARN_W = 11;
    private static final int WARN_X = VOID_X;
    private static final int WARN_Y = VOID_Y + BTN + 4;
    private static final int EYE_W = 12;

    private final HologramFacePanel facePanel = new HologramFacePanel(
            HologramFacePanel.columnsFor(ZoneMachineBlockEntity.SUPPORTED_FACE_BITS));

    private final ModuleBayPanel moduleBay = new ModuleBayPanel(
            () -> menu.activeModuleSlots(), menu::setModuleBayOpen);

    private final StatsPanel statsPanel = new StatsPanel(
            // Growth pace: the same x(2+pts)/2 as every machine's speed.
            new StatsPanel.Row(StatsPanel.Icon.SPEED,
                    () -> Component.literal("×" + String.format("%.1f",
                            (2 + modulePoints(ModuleType.SPEED)) / 2.0)),
                    Component.translatable("gui.krios_industry.stats.growth")),
            new StatsPanel.Row(StatsPanel.Icon.ENERGY_MULT,
                    () -> Component.literal("×" + String.format("%.2f",
                            KoiosBalance.efficiencyPermille(modulePoints(ModuleType.EFFICIENCY)) / 1000.0)),
                    Component.translatable("gui.krios_industry.stats.energy")),
            new StatsPanel.Row(StatsPanel.Icon.YIELD,
                    () -> Component.literal("+" + modulePoints(ModuleType.YIELD) * 25 + " %"),
                    Component.translatable("gui.krios_industry.stats.yield")),
            new StatsPanel.Row(StatsPanel.Icon.OUTPUT,
                    () -> Component.literal(menu.drawPerTick() + " E/t"),
                    Component.translatable("gui.krios_industry.stats.consumption")),
            new StatsPanel.Row(StatsPanel.Icon.ZONE,
                    () -> Component.literal(menu.workableCells() < 0 ? "—"
                            : Integer.toString(menu.workableCells())),
                    Component.translatable("gui.krios_industry.stats.cells")));

    private final AnnexPanel[] annexes = {moduleBay, statsPanel, facePanel};

    protected ZoneMachineScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private int modulePoints(ModuleType type) {
        int points = 0;
        for (int i = ZoneMachineBlockEntity.MODULE_SLOT_FIRST; i <= ZoneMachineBlockEntity.MODULE_SLOT_LAST; i++) {
            if (menu.getSlot(i).getItem().getItem() instanceof ModuleItem module
                    && module.getType() == type) {
                points += module.points();
            }
        }
        return points;
    }

    @Override
    protected AnnexPanel[] annexPanels() {
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

    // --- Work area ---

    @Override
    protected void renderWork(GuiGraphics gui) {
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
        drawEye(gui, leftPos + EYE_X, y, ZoneHologramState.isPinned(menu.machinePos()));
        drawVoidToggle(gui, leftPos + VOID_X, topPos + VOID_Y, menu.voidSurplus());
        if (menu.voidSurplus()) {
            drawWarning(gui, leftPos + WARN_X, topPos + WARN_Y);
        }
    }

    /** Seed glyph; struck through in red while surplus seeds are destroyed. */
    private static void drawVoidToggle(GuiGraphics gui, int x, int y, boolean on) {
        drawButton(gui, x, y, VOID_W, true);
        int seed = on ? 0xFF5FE09A : 0xFF2A7A52;
        gui.fill(x + 5, y + 2, x + 7, y + 3, seed);
        gui.fill(x + 4, y + 3, x + 8, y + 7, seed);
        gui.fill(x + 5, y + 7, x + 7, y + 8, seed);
        if (on) {
            int red = 0xFF000000 | COLOR_ERROR;
            for (int i = 0; i < 6; i++) {
                gui.fill(x + 3 + i, y + 7 - i, x + 4 + i, y + 8 - i, red);
            }
        }
    }

    /** Amber warning sign: a triangle with an exclamation mark. */
    private static void drawWarning(GuiGraphics gui, int x, int y) {
        int amber = 0xFF000000 | COLOR_ENERGY;
        for (int row = 0; row < 9; row++) {
            int half = row / 2;
            gui.fill(x + 5 - half, y + 1 + row, x + 6 + half, y + 2 + row, amber);
        }
        gui.fill(x + 5, y + 3, x + 6, y + 7, 0xFF10141B);
        gui.fill(x + 5, y + 8, x + 6, y + 9, 0xFF10141B);
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

    private static void drawButton(GuiGraphics gui, int x, int y, int w, boolean enabled) {
        gui.fill(x, y, x + w, y + BTN, 0xFF0C0E13);
        gui.fill(x + 1, y + 1, x + w - 1, y + BTN - 1, enabled ? 0xFF262B35 : 0xFF1B202A);
    }

    /** Eye glyph: lit cyan while the hologram is pinned. */
    private static void drawEye(GuiGraphics gui, int x, int y, boolean pinned) {
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

    private boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + w
                && mouseY >= topPos + y && mouseY < topPos + y + h;
    }

    private boolean minusHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, MINUS_X, ZoneMachineMenu.CONTROLS_Y, BTN, BTN);
    }

    private boolean plusHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, PLUS_X, ZoneMachineMenu.CONTROLS_Y, BTN, BTN);
    }

    private boolean eyeHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, EYE_X, ZoneMachineMenu.CONTROLS_Y, EYE_W, BTN);
    }

    private boolean voidHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, VOID_X, VOID_Y, VOID_W, BTN);
    }

    private boolean warnHit(double mouseX, double mouseY) {
        return menu.voidSurplus()
                && hit(mouseX, mouseY, WARN_X, WARN_Y, WARN_W, BTN);
    }

    private boolean mapHit(double mouseX, double mouseY) {
        return hit(mouseX, mouseY, ZoneMachineMenu.MAP_X, ZoneMachineMenu.MAP_Y, S, S);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        if (minusHit(mouseX, mouseY)) {
            gui.renderTooltip(font, Component.translatable("gui.krios_industry.zone.shrink"), mouseX, mouseY);
        } else if (plusHit(mouseX, mouseY)) {
            gui.renderTooltip(font, Component.translatable("gui.krios_industry.zone.grow",
                    menu.zoneMaxEdge(), menu.zoneMaxEdge()), mouseX, mouseY);
        } else if (eyeHit(mouseX, mouseY)) {
            boolean pinned = ZoneHologramState.isPinned(menu.machinePos());
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable(pinned ? "gui.krios_industry.zone.hologram_pinned"
                            : "gui.krios_industry.zone.hologram_show"),
                    Component.translatable("gui.krios_industry.zone.hologram_calibrator")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (voidHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable(menu.voidSurplus()
                            ? "gui.krios_industry." + plantableKey() + ".void_on" : "gui.krios_industry." + plantableKey() + ".void_off"),
                    Component.translatable("gui.krios_industry." + plantableKey() + ".refill")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (warnHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.krios_industry." + plantableKey() + ".warning_title")
                            .withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable("gui.krios_industry." + plantableKey() + ".warning")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (mapHit(mouseX, mouseY)) {
            gui.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.krios_industry.zone.map",
                            menu.zoneEdge(), menu.zoneEdge(), menu.zoneMaxEdge(), menu.zoneMaxEdge()),
                    Component.translatable("gui.krios_industry.zone.map_front")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (minusHit(mouseX, mouseY) && menu.zoneEdge() > 1) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        ZoneMachineBlockEntity.BUTTON_ZONE_SHRINK);
                return true;
            }
            if (plusHit(mouseX, mouseY) && menu.zoneEdge() < menu.zoneMaxEdge()) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        ZoneMachineBlockEntity.BUTTON_ZONE_GROW);
                return true;
            }
            if (voidHit(mouseX, mouseY)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        ZoneMachineBlockEntity.BUTTON_TOGGLE_VOID);
                return true;
            }
            if (eyeHit(mouseX, mouseY)) {
                ZoneHologramState.toggle(menu.machinePos());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Lang segment of this machine's voice lines (voice.{key}.*). */
    protected abstract String voiceKey();

    /** Lang segment of the plantable vocabulary (seeds / saplings). */
    protected abstract String plantableKey();

    @Override
    protected Component workingPhrase() {
        String base = "gui.krios_industry.voice." + voiceKey() + ".";
        return switch (menu.stateCode()) {
            case ZoneMachineBlockEntity.STATE_WORKING -> Component.translatable(base + "working");
            case ZoneMachineBlockEntity.STATE_BUFFER_FULL -> Component.translatable(base + "buffer_full");
            case ZoneMachineBlockEntity.STATE_NO_PLANTS -> Component.translatable(base + "no_seeds");
            case ZoneMachineBlockEntity.STATE_EMPTY_ZONE -> Component.translatable(base + "empty_zone");
            default -> menu.workableCells() > 0 ? Component.translatable(base + "waiting") : null;
        };
    }
}
