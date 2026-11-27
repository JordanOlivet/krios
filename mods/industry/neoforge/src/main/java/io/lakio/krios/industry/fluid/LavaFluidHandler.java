package io.lakio.krios.industry.fluid;

import io.lakio.krios.industry.machine.magma.LavaTank;
import io.lakio.krios.industry.machine.magma.MagmaReactorBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge view of the Magma Reactor's tank, one per external query (ADR-039).
 * Lava only. The face contract is read on every call, not when the capability
 * is asked for: a pipe that caches this handler sees a face the player changes
 * later. A null side (a query from no face) sees the tank, read-only.
 */
public record LavaFluidHandler(MagmaReactorBlockEntity reactor, @Nullable Direction side)
        implements IFluidHandler {

    private boolean canFill() {
        return side != null && reactor.allowsFluidInsert(side);
    }

    private boolean canDrain() {
        return side != null && reactor.allowsFluidExtract(side);
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        int stored = reactor.getLavaTank().getStored();
        return stored > 0 ? new FluidStack(Fluids.LAVA, stored) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return reactor.getLavaTank().getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return stack.is(Fluids.LAVA);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (!canFill() || resource.isEmpty() || !resource.is(Fluids.LAVA)) {
            return 0;
        }
        LavaTank tank = reactor.getLavaTank();
        int filled = tank.fill(resource.getAmount(), action.simulate());
        if (filled > 0 && action.execute()) {
            reactor.onLavaChanged();
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !resource.is(Fluids.LAVA)) {
            return FluidStack.EMPTY;
        }
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (!canDrain() || maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        int drained = reactor.getLavaTank().drain(maxDrain, action.simulate());
        if (drained <= 0) {
            return FluidStack.EMPTY;
        }
        if (action.execute()) {
            reactor.onLavaChanged();
        }
        return new FluidStack(Fluids.LAVA, drained);
    }
}
