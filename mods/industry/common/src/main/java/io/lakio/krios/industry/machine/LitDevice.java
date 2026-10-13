package io.lakio.krios.industry.machine;

import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.tick.DeviceHandle;
import io.lakio.krios.industry.tick.TickManager;
import io.lakio.krios.industry.tick.TickingDevice;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Device seat of the LIT-driven machines (reactor, grinder, forge, photon
 * array) on the ADR-027 scheduler: awake every tick while LIT, a slow probe
 * while a manual item face needs servicing (ADR-017 cadence), asleep
 * otherwise — until an alert (fuel or input in, a drain, a neighbor, a
 * face edit) or the LIT flip itself wakes it.
 */
public final class LitDevice {

    @Nullable
    private DeviceHandle handle;
    private long nextPull;

    public void register(Level level, TickingDevice owner) {
        if (level instanceof ServerLevel server) {
            remove();
            handle = TickManager.of(server).register(owner);
        }
    }

    public void remove() {
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }

    /** Something changed: visit at the next tick. */
    public void alert() {
        if (handle != null) {
            handle.alert();
        }
    }

    /** Manual item faces get serviced every ADR-017 interval. */
    public boolean pullDue(long now, FaceConfig faces) {
        if (!ItemFlow.hasManualItemFace(faces) || now < nextPull) {
            return false;
        }
        nextPull = now + ItemFlow.WAKE_INTERVAL_TICKS;
        return true;
    }

    /** End of a visit: awake while lit, probing for manual faces, else asleep. */
    public void settle(boolean lit, FaceConfig faces, long now) {
        if (handle == null) {
            return;
        }
        if (lit) {
            handle.stayAwake();
        } else if (ItemFlow.hasManualItemFace(faces)) {
            handle.sleepFor((int) Math.max(1, nextPull - now));
        } else {
            handle.sleep();
        }
    }

    /** A plain probe (the dark photon array asking "can I produce?"). */
    public void probe(boolean lit, int ticks) {
        if (handle == null) {
            return;
        }
        if (lit) {
            handle.stayAwake();
        } else {
            handle.sleepFor(ticks);
        }
    }
}
