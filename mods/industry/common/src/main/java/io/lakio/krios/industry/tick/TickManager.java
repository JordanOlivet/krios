package io.lakio.krios.industry.tick;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;

/**
 * The managed device scheduler (ADR-027): one per server level, ticked once
 * per level tick by each loader's hook. Awake devices are visited every
 * tick; a device sleeping until a game time stays in the list and costs one
 * comparison until then (zone probes, work cadences); a device asleep with
 * no deadline leaves the list and costs nothing until an alert wakes it.
 * Devices only run while their chunk ticks, like vanilla block entities.
 *
 * <p>Zero allocation in the tick path: one flat list compacted in place.
 * Server thread only.
 */
public final class TickManager {

    private static final Map<ServerLevel, TickManager> MANAGERS = new WeakHashMap<>();

    private final ServerLevel level;
    private final ArrayList<DeviceHandle> active = new ArrayList<>();
    private boolean ticking;

    private TickManager(ServerLevel level) {
        this.level = level;
    }

    public static TickManager of(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, TickManager::new);
    }

    /** Loader hook: once per level tick, server side. */
    public static void tickLevel(ServerLevel level) {
        TickManager manager = MANAGERS.get(level);
        if (manager != null) {
            manager.tick();
        }
    }

    /** Server stopping: forget every level (devices re-register on load). */
    public static void clear() {
        MANAGERS.clear();
    }

    /** Registers a device, awake: it ticks once on load, then sleeps if idle. */
    public DeviceHandle register(TickingDevice device) {
        DeviceHandle handle = new DeviceHandle(this, device);
        enlist(handle);
        return handle;
    }

    public long gameTime() {
        return level.getGameTime();
    }

    void enlist(DeviceHandle handle) {
        if (!handle.listed) {
            handle.listed = true;
            active.add(handle);
        }
    }

    private void tick() {
        ticking = true;
        long now = level.getGameTime();
        int write = 0;
        // Appends made during the pass (alerts, new devices) are visited
        // in the same pass: the loop reads the size each round.
        for (int read = 0; read < active.size(); read++) {
            DeviceHandle handle = active.get(read);
            if (handle.removed || handle.state == DeviceHandle.ASLEEP) {
                handle.listed = false;
                continue;
            }
            if (handle.wakeAt <= now && handle.lastTick != now
                    && level.shouldTickBlocksAt(handle.device.devicePos())) {
                handle.wakeAt = 0;
                handle.lastTick = now;
                handle.device.deviceTick(level, now);
            }
            if (handle.removed || handle.state == DeviceHandle.ASLEEP) {
                handle.listed = false;
            } else {
                active.set(write++, handle);
            }
        }
        for (int i = active.size() - 1; i >= write; i--) {
            active.remove(i);
        }
        ticking = false;
    }

    boolean isTicking() {
        return ticking;
    }
}
