package io.lakio.krios.industry.client;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Zone machines whose hologram the player pinned from the terminal (user
 * design 2026-10-08): during setup the zone stays visible without the
 * Calibrator in hand. A setup aid needs no server state: client-side, per
 * player, not persisted, dropped on any level change.
 */
public final class ZoneHologramState {

    private static final Set<BlockPos> PINNED = new HashSet<>();
    private static Level pinnedLevel;

    public static boolean isPinned(BlockPos pos) {
        return currentLevel() == pinnedLevel && PINNED.contains(pos);
    }

    public static void toggle(BlockPos pos) {
        Level level = currentLevel();
        if (level != pinnedLevel) {
            PINNED.clear();
            pinnedLevel = level;
        }
        if (!PINNED.remove(pos)) {
            PINNED.add(pos.immutable());
        }
    }

    /** Pinned positions in the current level (stale ones cleared first). */
    public static Set<BlockPos> pinned() {
        if (currentLevel() != pinnedLevel) {
            PINNED.clear();
            pinnedLevel = currentLevel();
        }
        return PINNED;
    }

    private static Level currentLevel() {
        return Minecraft.getInstance().level;
    }

    private ZoneHologramState() {
    }
}
