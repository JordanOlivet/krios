package io.lakio.krios.industry.client;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side blueprint anchor (a visual construction aid needs no server
 * state): the ground block the player clicked, the structure standing on
 * top of it. One blueprint at a time; clicking elsewhere moves it,
 * clicking the anchor again clears it.
 */
public final class BlueprintState {

    @Nullable
    private static BlockPos anchor;

    @Nullable
    public static BlockPos anchor() {
        return anchor;
    }

    /** Returns true when the toggle PLACED a blueprint (false = cleared). */
    public static boolean toggle(BlockPos clicked) {
        if (clicked.equals(anchor)) {
            anchor = null;
            return false;
        }
        anchor = clicked.immutable();
        return true;
    }

    public static void clear() {
        anchor = null;
    }

    private BlueprintState() {
    }
}
