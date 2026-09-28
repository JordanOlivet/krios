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

    /**
     * Serving tier: 0 is served first (e.g. working machines), 1 only sees
     * the surplus (e.g. idle buffers). Data declared by the member — the
     * engine stays balance-free (ADR-013).
     */
    default int sinkPriority() {
        return 0;
    }
}
