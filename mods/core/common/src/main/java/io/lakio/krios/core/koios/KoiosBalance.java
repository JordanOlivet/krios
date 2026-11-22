package io.lakio.krios.core.koios;

import io.lakio.krios.core.config.KriosConfig;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The KOIOS progression spine shared by every Krios mod (ADR-018): the
 * level curve, the tier ladder, the diversity window, and the single hookup
 * point of the ADR-007/ADR-015 difficulty multipliers (the {@code scaled*}
 * helpers). Content balance (per-operation XP and energy, machine rates,
 * unlock rungs) stays in each content mod. Values are
 * placeholders-by-default; rebalancing them is expected.
 */
public final class KoiosBalance {

    /** Sliding diversity window: the core remembers the last N operations. */
    public static final int WINDOW_SIZE = 64;

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
     * Difficulty-scaled per-operation energy cost (/krios_core config
     * energy_cost). Server-side only: a null or client level answers the
     * unscaled base.
     */
    public static int scaledEnergyCost(@Nullable Level level, int baseEnergy) {
        return KriosConfig.scale(baseEnergy, KriosConfig.permille(
                level == null ? null : level.getServer(), KriosConfig.Key.ENERGY_COST));
    }

    /**
     * Difficulty-scaled data XP for one reported operation (/krios_core
     * config xp_rate), with the diversity factor folded in.
     */
    public static int scaledOpXp(@Nullable Level level, int baseXp, int diversityPermille) {
        long ratePermille = KriosConfig.permille(
                level == null ? null : level.getServer(), KriosConfig.Key.XP_RATE);
        return (int) (baseXp * diversityPermille * ratePermille / 1_000_000L);
    }

    private KoiosBalance() {
    }
}
