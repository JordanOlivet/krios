package io.lakio.krios.industry.machine.magma;

import net.minecraft.nbt.CompoundTag;

/**
 * The Magma Reactor's lava, in millibuckets (design 2026-11-27). Primitive
 * ints like {@code EnergyBuffer}: game logic touches only this, the loaders'
 * fluid capabilities wrap it (NeoForge in mB, Fabric in droplets).
 */
public final class LavaTank {

    /** One bucket, in millibuckets. */
    public static final int BUCKET = 1000;

    private final int capacity;
    private int stored;

    public LavaTank(int capacity) {
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getStored() {
        return stored;
    }

    public int room() {
        return capacity - stored;
    }

    /** Transaction rollback (Fabric) and loading: clamped to the capacity. */
    public void setStored(int amount) {
        stored = Math.max(0, Math.min(capacity, amount));
    }

    /** Adds up to {@code amount} mB; returns what fitted. */
    public int fill(int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(amount, room()));
        if (!simulate) {
            stored += accepted;
        }
        return accepted;
    }

    /** Takes up to {@code amount} mB; returns what was there. */
    public int drain(int amount, boolean simulate) {
        int taken = Math.max(0, Math.min(amount, stored));
        if (!simulate) {
            stored -= taken;
        }
        return taken;
    }

    public void save(CompoundTag tag) {
        tag.putInt("Lava", stored);
    }

    public void load(CompoundTag tag) {
        setStored(tag.getInt("Lava"));
    }
}
