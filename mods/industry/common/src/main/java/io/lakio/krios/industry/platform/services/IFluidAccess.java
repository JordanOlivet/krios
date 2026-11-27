package io.lakio.krios.industry.platform.services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Lava through a block's fluid capability, as another mod's pipe sees it
 * (NeoForge {@code IFluidHandler}, Fabric {@code FluidStorage.SIDED}). The
 * Magma Reactor never needs it for itself; the GameTests use it to check
 * the face contract of its capability on each loader (ADR-039).
 */
public interface IFluidAccess {

    /** Lava pushed into the block at {@code pos} through {@code face}, in mB; returns what it took. */
    int insertLava(Level level, BlockPos pos, Direction face, int millibuckets);

    /** Lava pulled out of the block at {@code pos} through {@code face}, in mB; returns what came out. */
    int extractLava(Level level, BlockPos pos, Direction face, int millibuckets);
}
