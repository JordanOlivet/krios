package io.lakio.krios.industry.energy;

import net.minecraft.nbt.CompoundTag;

/**
 * Loader-neutral energy buffer. Primitive fields only (ADR-012): this is
 * per-machine runtime state touched every active tick.
 *
 * Platform adapters (FE on NeoForge, TRE on Fabric) wrap this class; game
 * logic never talks to a loader energy API directly.
 */
public final class EnergyBuffer {

    private final int capacity;
    private final int maxExtract;
    private int stored;

    public EnergyBuffer(int capacity, int maxExtract) {
        this.capacity = capacity;
        this.maxExtract = maxExtract;
    }

    /** Inserts up to {@code amount}, returns what was accepted. */
    public int insert(int amount) {
        int accepted = Math.min(amount, capacity - stored);
        stored += accepted;
        return accepted;
    }

    /** Extracts up to {@code min(amount, maxExtract)}, returns what was removed. */
    public int extract(int amount) {
        int removed = Math.min(Math.min(amount, maxExtract), stored);
        stored -= removed;
        return removed;
    }

    /**
     * Internal machine consumption: removes exactly {@code amount} or nothing.
     * Ignores maxExtract — that cap only applies to external extraction.
     */
    public boolean consume(int amount) {
        if (stored < amount) {
            return false;
        }
        stored -= amount;
        return true;
    }

    public int getStored() {
        return stored;
    }

    /** Direct restore, clamped to capacity — for transaction snapshot rollback. */
    public void setStored(int value) {
        stored = Math.max(0, Math.min(value, capacity));
    }

    public int getCapacity() {
        return capacity;
    }

    public int getMaxExtract() {
        return maxExtract;
    }

    public boolean isFull() {
        return stored >= capacity;
    }

    public void save(CompoundTag tag) {
        tag.putInt("Energy", stored);
    }

    public void load(CompoundTag tag) {
        stored = Math.min(tag.getInt("Energy"), capacity);
    }
}
