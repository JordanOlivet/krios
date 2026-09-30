package io.lakio.krios.industry.client;

import io.lakio.krios.industry.tool.Blueprint;
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
    private static Blueprint blueprint;

    @Nullable
    public static BlockPos anchor() {
        return anchor;
    }

    @Nullable
    public static Blueprint blueprint() {
        return blueprint;
    }

    /** Returns true when the toggle PLACED a blueprint (false = cleared). */
    public static boolean toggle(BlockPos clicked, Blueprint definition) {
        if (clicked.equals(anchor) && definition == blueprint) {
            clear();
            return false;
        }
        anchor = clicked.immutable();
        blueprint = definition;
        return true;
    }

    public static void clear() {
        anchor = null;
        blueprint = null;
    }

    private BlueprintState() {
    }
}
