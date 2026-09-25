package io.lakio.krios.industry.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A block entity participating in the network graph. Implementations
 * register in {@code setLevel} and unregister in {@code setRemoved} through
 * {@link NetworkManager}. Engine contract only — this package never imports
 * mod content (ADR-013).
 */
public interface NetworkNode {

    BlockPos networkPos();

    @Nullable
    Level networkLevel();

    /** Controllers govern a network; a valid network has exactly one active. */
    default boolean isController() {
        return false;
    }

    /** An inactive controller (e.g. unformed multiblock) counts as a plain node. */
    default boolean isActiveController() {
        return isController();
    }

    /** Called after this node's component is (re)computed. */
    default void onNetworkChanged(Network network) {
    }
}
