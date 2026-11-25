package io.lakio.krios.core.koios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KoiosBalanceTest {

    @Test
    void theLevelCurveOnlyClimbs() {
        for (int level = 2; level < KoiosBalance.LEVEL_XP.length; level++) {
            assertTrue(KoiosBalance.LEVEL_XP[level] > KoiosBalance.LEVEL_XP[level - 1],
                    "level " + level + " costs no more than level " + (level - 1));
        }
    }

    @Test
    void eachTierClosesOnOneCriticalLevel() {
        assertEquals(5, KoiosBalance.criticalLevel(1));
        assertEquals(9, KoiosBalance.criticalLevel(2));
        assertEquals(13, KoiosBalance.criticalLevel(3));
        assertEquals(4, KoiosBalance.maxClassicLevel(1));
        assertEquals(8, KoiosBalance.maxClassicLevel(2));
    }

    @Test
    void levelsFollowTheThresholdsExactly() {
        for (int level = 2; level <= KoiosBalance.maxClassicLevel(1); level++) {
            int threshold = KoiosBalance.LEVEL_XP[level];
            assertEquals(level - 1, KoiosBalance.levelForXp(threshold - 1, 1), "just under level " + level);
            assertEquals(level, KoiosBalance.levelForXp(threshold, 1), "at level " + level);
        }
        assertEquals(1, KoiosBalance.levelForXp(0, 1));
    }

    @Test
    void dataXpAloneNeverPassesTheTiersLastClassicLevel() {
        // The critical level is paid with a breakthrough, not with XP.
        assertEquals(4, KoiosBalance.levelForXp(Integer.MAX_VALUE, 1));
        assertEquals(8, KoiosBalance.levelForXp(Integer.MAX_VALUE, 2));
    }

    @Test
    void anUpgradedCoreClimbsTheNewStretch() {
        // At tier 2, the XP of tier 2's levels counts.
        assertEquals(6, KoiosBalance.levelForXp(KoiosBalance.LEVEL_XP[6], 2));
        assertEquals(8, KoiosBalance.levelForXp(KoiosBalance.LEVEL_XP[8], 2));
    }

    @Test
    void xpStopsAtTheCriticalThreshold() {
        assertEquals(KoiosBalance.LEVEL_XP[5], KoiosBalance.xpCap(1));
        assertEquals(KoiosBalance.LEVEL_XP[9], KoiosBalance.xpCap(2));
        // Past the last defined level, the last one caps.
        int last = KoiosBalance.LEVEL_XP[KoiosBalance.LEVEL_XP.length - 1];
        assertEquals(last, KoiosBalance.xpCap(KoiosBalance.MAX_TIER));
    }

    @Test
    void aTierUpNeedsTheNextTiersCurve() {
        // Callers ask for the tier a breakthrough would open (tier + 1).
        assertTrue(KoiosBalance.hasTierCurve(2), "tier 2 ships: a tier 1 core can break through");
        assertFalse(KoiosBalance.hasTierCurve(3), "tier 3 has no curve yet: a tier 2 core stays");
    }

    @Test
    void projectionsSurfaceAtEightyPercentOfTheCap() {
        int cap = KoiosBalance.xpCap(1);
        int threshold = cap * KoiosBalance.PROJECTIONS_THRESHOLD_PERMILLE / 1000;
        assertFalse(KoiosBalance.projectionsActive(threshold - 1, 1));
        assertTrue(KoiosBalance.projectionsActive(threshold, 1));
        assertTrue(KoiosBalance.projectionsActive(cap, 1));
    }
}
