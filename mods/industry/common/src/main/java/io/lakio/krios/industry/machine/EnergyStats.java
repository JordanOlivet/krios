package io.lakio.krios.industry.machine;

/**
 * Live energy accounting a machine exposes for diagnostics (Calibrator,
 * KOIOS alerts). Industry-side, not engine-side: the network engine stays
 * balance-free (ADR-013), consumers of the graph do the bookkeeping.
 */
public interface EnergyStats {

    /** Current demand while working, in E/t (0 while idle). */
    int energyDrawPerTick();

    /** Current production while running, in E/t (0 while idle). */
    int energySupplyPerTick();

    /** E actually consumed on the last work tick (0 while idle or starved). */
    int energyConsumedPerTick();

    /** Working, but the last tick's energy consume failed (frozen machine). */
    boolean energyStarved();

    int energyStored();

    int energyCapacity();
}
