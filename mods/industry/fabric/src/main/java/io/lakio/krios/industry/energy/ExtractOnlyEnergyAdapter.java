package io.lakio.krios.industry.energy;

import io.lakio.krios.industry.machine.generator.GeneratorBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/**
 * TRE view of the internal buffer: neighbors may pull, never push.
 * SnapshotParticipant makes extraction transaction-safe (rollback restores
 * the buffered amount).
 */
public class ExtractOnlyEnergyAdapter extends SnapshotParticipant<Integer> implements EnergyStorage {

    private final GeneratorBlockEntity generator;

    public ExtractOnlyEnergyAdapter(GeneratorBlockEntity generator) {
        this.generator = generator;
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
        EnergyBuffer buffer = generator.getEnergy();
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
        return generator.getEnergy().getStored();
    }

    @Override
    public long getCapacity() {
        return generator.getEnergy().getCapacity();
    }

    @Override
    protected Integer createSnapshot() {
        return generator.getEnergy().getStored();
    }

    @Override
    protected void readSnapshot(Integer snapshot) {
        generator.getEnergy().setStored(snapshot);
    }

    @Override
    protected void onFinalCommit() {
        generator.setChanged();
    }
}
