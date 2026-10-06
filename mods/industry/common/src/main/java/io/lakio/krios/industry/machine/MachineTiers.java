package io.lakio.krios.industry.machine;

import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.network.Network;
import org.jetbrains.annotations.Nullable;

/**
 * The tier math shared by every machine (design 2026-09-30).
 *
 * One founding rule: <b>effective tier = min(machine tier, core tier)</b>
 * while online, tier 1 baseline otherwise — hardware performs, software
 * caps, in both directions. One invariant: <b>each tier doubles both
 * sides</b> (generation and consumption speed), so the park ratio — one
 * reactor feeds four plasma forges — holds at every tier, and a
 * fuel's energy value stays constant since it burns faster at the same
 * per-fuel total.
 */
public final class MachineTiers {

    /**
     * Reactor throughput multiplier per effective tier: 1, 2, 4, 8 —
     * rate AND burn speed together, so a fuel's total energy is constant.
     */
    public static int speedFactor(int tier) {
        return 1 << (tier - 1);
    }

    /**
     * Parallel process columns per tier: 1, 2, 4, 8 (Mekanism-factory
     * reference, settled 2026-09-30 evening). Processing machines gain
     * COLUMNS with tiers, not speed — per-column pace stays the baseline
     * (2x vanilla + modules) and energy per op is unchanged, so the park
     * ratio holds while operations become visibly parallel.
     */
    public static int processSlots(int tier) {
        return 1 << (tier - 1);
    }

    /**
     * Grinder base yield ladder over 2: x2 / x3 / x3 / x4 (tier 3 breathes,
     * design). Quarter-item accumulator keeps it deterministic (ADR-006).
     * The grinder is the yield machine — the forge does not compound it.
     */
    private static final int[] YIELD_NUM = {2, 3, 3, 4};

    public static int yieldNum(int tier) {
        return YIELD_NUM[tier - 1];
    }

    /** Effective tier: min(machine, core) online; offline = tier 1 baseline. */
    public static int effectiveTier(@Nullable Network network, int machineTier) {
        return network != null && network.isValid()
                && network.controller() instanceof KoiosCoreBlockEntity core
                ? Math.min(machineTier, core.getTier()) : 1;
    }

    private MachineTiers() {
    }
}
