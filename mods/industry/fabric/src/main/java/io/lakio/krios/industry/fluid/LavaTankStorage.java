package io.lakio.krios.industry.fluid;

import io.lakio.krios.industry.machine.magma.LavaTank;
import io.lakio.krios.industry.machine.magma.MagmaReactorBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Transfer API view of the Magma Reactor's tank, one per external query
 * (ADR-039). The tank counts millibuckets, Fabric counts droplets (81 per
 * mB): only whole millibuckets move. SnapshotParticipant makes it
 * transaction-safe; the reactor hears of a change on the final commit only.
 */
public class LavaTankStorage extends SnapshotParticipant<Integer> implements SingleSlotStorage<FluidVariant> {

    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / LavaTank.BUCKET;
    private static final FluidVariant LAVA = FluidVariant.of(Fluids.LAVA);

    private final MagmaReactorBlockEntity reactor;
    /** The face this view was asked for; null (no face) sees the tank, read-only. */
    @Nullable
    private final Direction side;

    public LavaTankStorage(MagmaReactorBlockEntity reactor, @Nullable Direction side) {
        this.reactor = reactor;
        this.side = side;
    }

    // The face contract is read on every call: a cached view sees a face
    // the player changes later.
    @Override
    public boolean supportsInsertion() {
        return side != null && reactor.allowsFluidInsert(side);
    }

    @Override
    public boolean supportsExtraction() {
        return side != null && reactor.allowsFluidExtract(side);
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (!supportsInsertion() || !resource.isOf(Fluids.LAVA)) {
            return 0;
        }
        LavaTank tank = reactor.getLavaTank();
        int mb = (int) Math.min(maxAmount / DROPLETS_PER_MB, tank.room());
        if (mb <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        return tank.fill(mb, false) * DROPLETS_PER_MB;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (!supportsExtraction() || !resource.isOf(Fluids.LAVA)) {
            return 0;
        }
        LavaTank tank = reactor.getLavaTank();
        int mb = (int) Math.min(maxAmount / DROPLETS_PER_MB, tank.getStored());
        if (mb <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        return tank.drain(mb, false) * DROPLETS_PER_MB;
    }

    @Override
    public boolean isResourceBlank() {
        return reactor.getLavaTank().getStored() == 0;
    }

    @Override
    public FluidVariant getResource() {
        return isResourceBlank() ? FluidVariant.blank() : LAVA;
    }

    @Override
    public long getAmount() {
        return reactor.getLavaTank().getStored() * DROPLETS_PER_MB;
    }

    @Override
    public long getCapacity() {
        return reactor.getLavaTank().getCapacity() * DROPLETS_PER_MB;
    }

    @Override
    protected Integer createSnapshot() {
        return reactor.getLavaTank().getStored();
    }

    @Override
    protected void readSnapshot(Integer snapshot) {
        reactor.getLavaTank().setStored(snapshot);
    }

    @Override
    protected void onFinalCommit() {
        reactor.onLavaChanged();
    }
}
