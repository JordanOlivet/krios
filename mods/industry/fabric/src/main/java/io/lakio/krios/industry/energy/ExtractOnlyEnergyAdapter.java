package io.lakio.krios.industry.energy;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/**
 * TRE view of an internal buffer: neighbors may pull, never push.
 * SnapshotParticipant makes extraction transaction-safe (rollback restores
 * the buffered amount). Producer-agnostic: reactor and photon array both
 * hand their buffer plus their own commit hook (setChanged).
 */
public class ExtractOnlyEnergyAdapter extends SnapshotParticipant<Integer> implements EnergyStorage {

    private final EnergyBuffer buffer;
    private final Runnable onCommit;

    public ExtractOnlyEnergyAdapter(EnergyBuffer buffer, Runnable onCommit) {
        this.buffer = buffer;
        this.onCommit = onCommit;
    }

    @Override
    public boolean supportsInsertion() {
        return false;
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public boolean supportsExtraction() {
        return true;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        int desired = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        int extractable = Math.min(desired, Math.min(buffer.getMaxExtract(), buffer.getStored()));
        if (extractable <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        return buffer.extract(extractable);
    }

    @Override
    public long getAmount() {
        return buffer.getStored();
    }

    @Override
    public long getCapacity() {
        return buffer.getCapacity();
    }

    @Override
    protected Integer createSnapshot() {
        return buffer.getStored();
    }

    @Override
    protected void readSnapshot(Integer snapshot) {
        buffer.setStored(snapshot);
    }

    @Override
    protected void onFinalCommit() {
        onCommit.run();
    }
}
