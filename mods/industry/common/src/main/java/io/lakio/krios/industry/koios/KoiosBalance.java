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

    /** Base data XP per completed grinding operation. */
    public static final int GRIND_BASE_XP = 10;
    /** Base data XP per completed smelting operation (plasma forge). */
    public static final int SMELT_BASE_XP = 6;
    /** Base data XP per reactor burn cycle (per fuel item consumed). */
    public static final int BURN_BASE_XP = 2;

    /**
     * Self-sufficient processes teach KOIOS less (machine roadmap
     * 2026-10-06): one permille constant per machine class, calibrated
     * during the survival playthrough. Farms: a fifth of a grind.
     */
    public static final int FARM_XP_PERMILLE = 200;
    /** Base data XP per crop harvested by a Chrono Farmer. */
    public static final int HARVEST_BASE_XP = GRIND_BASE_XP * FARM_XP_PERMILLE / 1000;

    // Chrono Farmer energy, per action (placeholders until the numbers
    // pass; sized on park ratios then, per the energy-scale method).
    public static final int HARVEST_ENERGY = 60;
    public static final int PLANT_ENERGY = 20;
    public static final int TILL_ENERGY = 20;
    /** Topping one farmland block back up to full moisture. */
    public static final int HYDRATE_ENERGY = 5;

    /** Forester class: a whole tree teaches half a grind (placeholder). */
    public static final int FORESTER_XP_PERMILLE = 500;
    /** Base data XP per tree felled by a Cyclic Forester. */
    public static final int FELL_BASE_XP = GRIND_BASE_XP * FORESTER_XP_PERMILLE / 1000;
    // Cyclic Forester energy, per block cut (placeholders, numbers pass).
    public static final int LOG_CUT_ENERGY = 30;
    public static final int LEAF_CUT_ENERGY = 5;
    /** Blocks cut per wake while felling, x effective-tier speed factor. */
    public static final int FELL_BLOCKS_BASE = 8;
    /** One extra random tick forced on a growing crop (Speed modules). */
    public static final int GROWTH_TICK_ENERGY = 15;

    /**
     * Zone machine pacing (ADR-022): a wake that acted comes back after
     * ACTIVE ticks, a full pass with nothing to do backs off to IDLE. Each
     * wake performs at most ACTIONS_BASE x effective-tier speed factor
     * actions, so a ripe field is swept visibly instead of in one tick.
     */
    public static final int ZONE_ACTIVE_DELAY_TICKS = 10;
    public static final int ZONE_IDLE_DELAY_TICKS = 100;
    public static final int ZONE_ACTIONS_BASE = 4;
    /** Forced growth ticks per wake are capped (no burst after a long gap). */
    public static final int ZONE_MAX_GROWTH_TICKS_PER_WAKE = 64;

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
    // Tier 2 ladder (design 2026-10-01): N6 = Speed II + the energy
    // module (user call), N7 = Efficiency II, N8 = Yield II (capstone).
    public static final int SPEED2_UNLOCK_LEVEL = 6;
    public static final int ENERGY_UNLOCK_LEVEL = 6;
    public static final int EFFICIENCY2_UNLOCK_LEVEL = 7;
    public static final int YIELD2_UNLOCK_LEVEL = 8;

    /**
     * Efficiency multiplier per POINT total, permille: ~x0.8 per point
     * (points model 2026-10-01 — Mk I = 1 pt, Mk II = 2). Sized for the
     * t2 ceiling (4 slots x 2 pts); clamped above.
     */
    private static final int[] EFFICIENCY_PERMILLE =
            {1000, 800, 640, 512, 410, 328, 262, 210, 168};

    public static int efficiencyPermille(int points) {
        return EFFICIENCY_PERMILLE[Math.min(points, EFFICIENCY_PERMILLE.length - 1)];
    }

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

    /**
     * Codex Projections threshold (design 2026-10-05): as XP nears the
     * tier's cap, KOIOS starts "dreaming" of its next form — next-tier
     * projection content surfaces in the Codex.
     */
    public static final int PROJECTIONS_THRESHOLD_PERMILLE = 800;

    /** XP close enough to this tier's cap that projections surface. */
    public static boolean projectionsActive(int xp, int tier) {
        return (long) xp * 1000 >= (long) xpCap(tier) * PROJECTIONS_THRESHOLD_PERMILLE;
    }

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
