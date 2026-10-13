package io.lakio.krios.industry.tick;

/**
 * A device's seat in its level's {@link TickManager} (ADR-027). The device
 * keeps it and says how it wants to be ticked next:
 *
 * <ul>
 * <li>{@link #stayAwake()}: every tick;</li>
 * <li>{@link #sleepFor(int)}: once that many ticks have passed (a probe,
 * or a work cadence) — one comparison per tick until then;</li>
 * <li>{@link #sleep()}: not at all, until an alert;</li>
 * <li>{@link #alert()} / {@link #alertWithin(int)}: something changed —
 * wake now, or make sure a wake comes within N ticks (never postpones an
 * earlier one).</li>
 * </ul>
 */
public final class DeviceHandle {

    static final int AWAKE = 0;
    static final int ASLEEP = 1;

    private final TickManager manager;
    final TickingDevice device;
    int state = AWAKE;
    /** Game time of the next visit; 0 = next tick. */
    long wakeAt;
    boolean listed;
    /** Last game time visited: an alert re-listing a device mid-pass never ticks it twice. */
    long lastTick = -1;
    boolean removed;

    DeviceHandle(TickManager manager, TickingDevice device) {
        this.manager = manager;
        this.device = device;
    }

    public void stayAwake() {
        state = AWAKE;
        wakeAt = 0;
        manager.enlist(this);
    }

    public void sleepFor(int ticks) {
        state = AWAKE;
        wakeAt = manager.gameTime() + Math.max(1, ticks);
        manager.enlist(this);
    }

    public void sleep() {
        state = ASLEEP;
    }

    /** Wake at the next tick (this very pass if it has not reached the device yet). */
    public void alert() {
        if (removed) {
            return;
        }
        stayAwake();
    }

    /** Make sure a visit comes within {@code ticks}; an earlier one stays. */
    public void alertWithin(int ticks) {
        if (removed) {
            return;
        }
        long target = manager.gameTime() + Math.max(1, ticks);
        if (state == ASLEEP || wakeAt > target) {
            state = AWAKE;
            wakeAt = target;
        }
        manager.enlist(this);
    }

    /** The device left the level (removed, chunk unloaded). */
    public void remove() {
        removed = true;
    }

    public boolean isAsleep() {
        return state == ASLEEP;
    }

    public long wakeAt() {
        return wakeAt;
    }
}
