package io.lakio.krios.industry.machine;

/**
 * A machine block that exists at a hardware tier (Mekanism-style ladder,
 * design 2026-09-30). The block entity reads its tier from here; what the
 * machine actually delivers is the EFFECTIVE tier — min(machine, core) —
 * computed per tick in {@link MachineTiers}.
 */
public interface TieredMachine {

    int machineTier();
}
