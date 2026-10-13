package io.lakio.krios.industry.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A machine driven by the {@link TickManager} (ADR-027). Each visit ends
 * with the device telling its {@link DeviceHandle} when it wants the next
 * one; a device that says nothing stays awake.
 */
public interface TickingDevice {

    void deviceTick(ServerLevel level, long gameTime);

    BlockPos devicePos();
}
