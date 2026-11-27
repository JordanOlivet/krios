package io.lakio.krios.industry.platform;

import io.lakio.krios.industry.platform.services.IFluidAccess;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

/** Lava through the Transfer API, converted between millibuckets and droplets. */
public class FabricFluidAccess implements IFluidAccess {

    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    @Override
    public int insertLava(Level level, BlockPos pos, Direction face, int millibuckets) {
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, face);
        if (storage == null) {
            return 0;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long moved = storage.insert(FluidVariant.of(Fluids.LAVA), millibuckets * DROPLETS_PER_MB, transaction);
            transaction.commit();
            return (int) (moved / DROPLETS_PER_MB);
        }
    }

    @Override
    public int extractLava(Level level, BlockPos pos, Direction face, int millibuckets) {
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, face);
        if (storage == null) {
            return 0;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long moved = storage.extract(FluidVariant.of(Fluids.LAVA), millibuckets * DROPLETS_PER_MB, transaction);
            transaction.commit();
            return (int) (moved / DROPLETS_PER_MB);
        }
    }
}
