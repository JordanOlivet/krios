package io.lakio.krios.industry.dev;

/**
 * Tick-model benchmark modes (responsiveness question, 2026-10-13).
 * Temporary dev tooling: removed once the ADR settles the model.
 */
public enum BenchMode {
    /** Nodes placed, nothing ticks: the floor. */
    BASELINE,
    /** A vanilla ticker that returns at once: the pure ticker overhead. */
    TICKER_EMPTY,
    /** A vanilla ticker running a machine-like loop: per-tick check, batch every 10 ticks. */
    TICKER_WORK,
    /** Same batch on a vanilla scheduled tick every 10 ticks (today's zone machines). */
    SCHEDULED,
    /** Same loop as TICKER_WORK, driven by one manager iterating plain objects (AE2-like). */
    MANAGED,
    /** TICKER_WORK plus setChanged() every tick: the classic trap. */
    TICKER_DIRTY;

    public static final BenchMode[] VALUES = values();
}
