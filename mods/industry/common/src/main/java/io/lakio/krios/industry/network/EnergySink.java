package io.lakio.krios.industry.network;

/**
 * A network member that can receive pushed energy (producers distribute
 * proactively so idle machine buffers fill as soon as energy exists). The
 * implementation applies its own rate caps — the engine only aggregates
 * (ADR-013: no balance here).
 */
public interface EnergySink {

    /** Offer up to {@code max} to this member, returns what it accepted. */
    int insertNetworkEnergy(int max);
}
