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
    int MODULE_SLOT_X = -40;
    int MODULE_SLOT_Y = 104;
    int MODULE_SLOT_STEP = 22;

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
