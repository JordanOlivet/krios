package io.lakio.krios.industry.tick;

/** A machine others may wake (ADR-027): neighbors, item flows, the upgrade kit. */
public interface DeviceAlertable {

    void alertDevice();
}
