package io.lakio.krios.industry.platform;

import io.lakio.krios.industry.platform.services.IFluidAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Lava through the NeoForge fluid capability, in millibuckets. */
public class NeoForgeFluidAccess implements IFluidAccess {

    @Override
    public int insertLava(Level level, BlockPos pos, Direction face, int millibuckets) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        return handler == null ? 0
                : handler.fill(new FluidStack(Fluids.LAVA, millibuckets), IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public int extractLava(Level level, BlockPos pos, Direction face, int millibuckets) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        return handler == null ? 0
                : handler.drain(new FluidStack(Fluids.LAVA, millibuckets), IFluidHandler.FluidAction.EXECUTE)
                        .getAmount();
    }
}
