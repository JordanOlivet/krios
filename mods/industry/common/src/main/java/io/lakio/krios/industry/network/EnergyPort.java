package io.lakio.krios.industry.network;

/**
 * A network member that can contribute energy to the pool. The
 * implementation applies its own rate caps and side effects (wake-ups,
 * dirty flags) — the engine only aggregates (ADR-013: no balance here).
 */
public interface EnergyPort {

    /** Extract up to {@code max} from this member, returns what was taken. */
    int extractNetworkEnergy(int max);
}
