package io.lakio.krios.industry.machine;

import net.minecraft.core.BlockPos;

/**
 * Shared surface of the three machine menus, consumed by the client
 * MachineTerminalScreen (codex terminal + annex panels). Implementations
 * already sync everything through ContainerData — this only names it.
 */
public interface MachineMenu {

    /**
     * Module-bay slot geometry, relative to the screen's leftPos/topPos —
     * negative X: the slots live on the LEFT annex, outside the main frame.
     * Shared with the client ModuleBayPanel, which derives its dock and
     * width from these numbers; keep both in sync.
     */
    int MODULE_SLOT_X = -49;
    int MODULE_SLOT_Y = 109;
    int MODULE_SLOT_STEP = 22;

    /**
     * Process column geometry (Mekanism-style vertical columns in the work
     * center: input on top, progress bar, output below). Shared by the
     * crusher/furnace menus and screens; indexes follow the column order.
     */
    int[] PROCESS_COL_X = {105, 135};
    int PROCESS_IN_Y = 26;
    int PROCESS_OUT_Y = 64;
    int PROCESS_BAR_Y = 47;
    int PROCESS_BAR_H = 14;

    /**
     * Vanilla menu-button id (outside the face channel, faces use 0..95)
     * toggling the input distribution between fill-first and balance.
     */
    int BUTTON_TOGGLE_DISTRIBUTION = 100;

    /** Input distribution mode (synced); false = fill first (default). */
    default boolean isInputBalance() {
        return false;
    }

    /** Raw face-config bits for face 0..5 (decode via FaceConfig statics). */
    int faceBits(int face);

    /** Machine position, for the hologram viewport. */
    BlockPos machinePos();

    /** KOIOS level of the network controller; 0 while offline. */
    int networkLevel();

    int getEnergyStored();

    int getEnergyCapacity();

    /** 0..width pixels of stored energy (gauge). */
    int getEnergyProgress(int width);

    /** Working but frozen for lack of energy (synced flag). */
    default boolean isStarved() {
        return false;
    }

    /**
     * Client-side module bay visibility, consulted by the module slots'
     * isActive(). Server menu instances never toggle it — hidden slots
     * still accept server-routed moves (shift-click), by design.
     */
    default void setModuleBayOpen(boolean open) {
    }
}
