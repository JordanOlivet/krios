package io.lakio.krios.industry.machine.module;

/**
 * A module's AXIS. The magnitude lives on the item as POINTS (design
 * 2026-10-01: Mk I = 1, Mk II = 2; a future "infinity" module is just an
 * absurd count) — every machine formula takes the per-axis point total,
 * so marks mix freely in the same slots. ENERGY (+25%/pt energy from the
 * same fuel) is accepted by generators only.
 */
public enum ModuleType {
    SPEED,
    EFFICIENCY,
    YIELD,
    ENERGY;
}
