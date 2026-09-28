package io.lakio.krios.industry.koios;

/**
 * Central balance knobs for the KOIOS progression. Kept in one place so the
 * planned difficulty config (ADR-007) has a single hookup point. Values are
 * placeholders-by-default; rebalancing them is expected.
 */
public final class KoiosBalance {

    /** Sliding diversity window: the core remembers the last N operations. */
    public static final int WINDOW_SIZE = 64;

    /** Base data XP per completed crushing operation. */
    public static final int CRUSH_BASE_XP = 10;
    /** Base data XP per generator burn cycle (per fuel item consumed). */
    public static final int BURN_BASE_XP = 2;

    // Diversity factor, per-mille: full value while an operation stays under
    // KNEE share of the window, then linear falloff down to FLOOR at pure
    // repetition. Anti-AFK by construction; recovers as soon as you vary.
    public static final int DIVERSITY_KNEE_PERMILLE = 250;
    // 20% floor (was 10%): pure repetition at level 1 was too punishing —
    // ~500 mono-recipe ops to reach N2 with slow tier 1 machines.
    public static final int DIVERSITY_FLOOR_PERMILLE = 200;

    /** Highest classic level reachable through data XP alone (tier 1). */
    public static final int MAX_CLASSIC_LEVEL = 4;
    /** Critical level gating tier 2 (items + XP; locked until tier 2 ships). */
    public static final int CRITICAL_LEVEL = 5;

    /** Cumulative data XP required to reach each level (index = level). */
    public static final int[] LEVEL_XP = {0, 0, 500, 1500, 3500, 6500};

    public static final int SPEED_UNLOCK_LEVEL = 2;
    public static final int EFFICIENCY_UNLOCK_LEVEL = 3;
    public static final int YIELD_UNLOCK_LEVEL = 4;

    /**
     * Vanilla parity while disconnected from a valid network (design):
     * furnace-grade operation time (a furnace smelt is 200 ticks), half the
     * connected recipe yield (x2 recipes fall back to 1:1, never below one
     * item), and modules inert.
     */
    public static final int DISCONNECTED_TIME_TICKS = 200;

    /**
     * Search radius of the /koios admin command (nearest active core). XP
     * reporting itself goes through the network graph, not a radius.
     */
    public static final int CORE_RANGE = 32;
    public static final int CORE_RANGE_SQ = CORE_RANGE * CORE_RANGE;

    public static int levelForXp(int xp) {
        int level = 1;
        while (level < MAX_CLASSIC_LEVEL && xp >= LEVEL_XP[level + 1]) {
            level++;
        }
        return level;
    }

    /** Data XP accrual stops at the critical threshold until it is paid. */
    public static int xpCap() {
        return LEVEL_XP[CRITICAL_LEVEL];
    }

    private KoiosBalance() {
    }
}
