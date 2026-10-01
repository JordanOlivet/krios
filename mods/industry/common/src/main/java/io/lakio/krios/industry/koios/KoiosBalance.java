package io.lakio.krios.industry.koios;

import io.lakio.krios.industry.config.IndustryConfig;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Central balance knobs for the KOIOS progression, and the single hookup
 * point of the ADR-007/ADR-015 difficulty multipliers (the {@code scaled*}
 * helpers). Values are placeholders-by-default; rebalancing them is expected.
 */
public final class KoiosBalance {

    /** Sliding diversity window: the core remembers the last N operations. */
    public static final int WINDOW_SIZE = 64;

    /** Base data XP per completed crushing operation. */
    public static final int CRUSH_BASE_XP = 10;
    /** Base data XP per completed smelting operation (induction furnace). */
    public static final int SMELT_BASE_XP = 6;
    /** Base data XP per generator burn cycle (per fuel item consumed). */
    public static final int BURN_BASE_XP = 2;

    // Diversity factor, per-mille: full value while an operation stays under
    // KNEE share of the window, then linear falloff down to FLOOR at pure
    // repetition. Anti-AFK by construction; recovers as soon as you vary.
    public static final int DIVERSITY_KNEE_PERMILLE = 250;
    // 20% floor (was 10%): pure repetition at level 1 was too punishing —
    // ~500 mono-recipe ops to reach N2 with slow tier 1 machines.
    public static final int DIVERSITY_FLOOR_PERMILLE = 200;

    /**
     * Tier ladder (design 2026-09-30): continuous level numbering, four
     * classic levels then one critical level per tier. Tier 1 = N1-N4
     * classic, N5 critical (paid with a compute chip, XP is a threshold
     * and is never consumed). Tier 2 = N6-N8 classic (Mk II modules),
     * N9 critical toward tier 3.
     */
    public static final int MAX_TIER = 4;

    /**
     * Cumulative data XP required to reach each level (index = level).
     * Extended per tier as its content ships; a tier-up is only possible
     * while the NEXT tier's curve exists (see {@link #hasTierCurve}).
     */
    public static final int[] LEVEL_XP = {0, 0, 500, 1500, 3500, 6500,
            12_000, 20_000, 30_000, 42_000};

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
     * Items pulled from an adjacent passive container per scheduled wake
     * (~1.5 s, ADR-017), times the effective-tier speed factor — 8/16/32/64.
     * The only transport number: machine-to-machine batches are the
     * produced lot, uncapped (design 2026-10-01).
     */
    public static final int ITEM_PULL_BATCH_BASE = 8;

    /**
     * Search radius of the /koios admin command (nearest active core). XP
     * reporting itself goes through the network graph, not a radius.
     */
    public static final int CORE_RANGE = 32;
    public static final int CORE_RANGE_SQ = CORE_RANGE * CORE_RANGE;

    /** The critical level closing a tier: 5, 9, 13... (one per boundary). */
    public static int criticalLevel(int tier) {
        return 1 + 4 * tier;
    }

    /** Highest classic level reachable through data XP alone at this tier. */
    public static int maxClassicLevel(int tier) {
        return criticalLevel(tier) - 1;
    }

    /** The next tier's curve is defined — its content shipped. */
    public static boolean hasTierCurve(int tier) {
        return criticalLevel(tier) < LEVEL_XP.length;
    }

    /**
     * Level for an XP total at a given core tier. A freshly upgraded core
     * lands ON its previous critical level (paid, acquired) and climbs the
     * new classic stretch from there.
     */
    public static int levelForXp(int xp, int tier) {
        int level = 1;
        while (level < maxClassicLevel(tier)
                && level + 1 < LEVEL_XP.length && xp >= LEVEL_XP[level + 1]) {
            level++;
        }
        return level;
    }

    /**
     * Data XP accrual stops at the tier's critical threshold until the
     * breakthrough is paid; the final defined level caps the last tier.
     */
    public static int xpCap(int tier) {
        int critical = criticalLevel(tier);
        return LEVEL_XP[Math.min(critical, LEVEL_XP.length - 1)];
    }

    /**
     * Difficulty-scaled per-operation energy cost (/krios_industry config
     * energy_cost). Server-side only: a null or client level answers the
     * unscaled base.
     */
    public static int scaledEnergyCost(@Nullable Level level, int baseEnergy) {
        return IndustryConfig.scale(baseEnergy, IndustryConfig.permille(
                level == null ? null : level.getServer(), IndustryConfig.Key.ENERGY_COST));
    }

    /**
     * Difficulty-scaled data XP for one reported operation (/krios_industry
     * config xp_rate), with the diversity factor folded in.
     */
    public static int scaledOpXp(@Nullable Level level, int baseXp, int diversityPermille) {
        long ratePermille = IndustryConfig.permille(
                level == null ? null : level.getServer(), IndustryConfig.Key.XP_RATE);
        return (int) (baseXp * diversityPermille * ratePermille / 1_000_000L);
    }

    private KoiosBalance() {
    }
}
