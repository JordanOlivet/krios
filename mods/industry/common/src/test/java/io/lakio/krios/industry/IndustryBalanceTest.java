package io.lakio.krios.industry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lakio.krios.core.koios.KoiosBalance;
import org.junit.jupiter.api.Test;

/**
 * Industry's numbers against core's ladder: an unlock a tier cannot reach,
 * or one placed on a critical level, would be a module nobody can insert.
 */
class IndustryBalanceTest {

    private static void assertReachableInTier(int level, int tier, String what) {
        int lowest = tier == 1 ? 2 : KoiosBalance.criticalLevel(tier - 1) + 1;
        assertTrue(level >= lowest && level <= KoiosBalance.maxClassicLevel(tier),
                what + " at level " + level + " is outside tier " + tier + "'s classic levels "
                        + lowest + ".." + KoiosBalance.maxClassicLevel(tier));
    }

    @Test
    void tierOneUnlocksAreTierOneClassicLevels() {
        assertReachableInTier(IndustryBalance.SPEED_UNLOCK_LEVEL, 1, "speed");
        assertReachableInTier(IndustryBalance.EFFICIENCY_UNLOCK_LEVEL, 1, "efficiency");
        assertReachableInTier(IndustryBalance.YIELD_UNLOCK_LEVEL, 1, "yield");
        assertReachableInTier(IndustryBalance.LURE_UNLOCK_LEVEL, 1, "lure");
        assertReachableInTier(IndustryBalance.LUCK_UNLOCK_LEVEL, 1, "luck");
    }

    @Test
    void tierTwoUnlocksAreTierTwoClassicLevels() {
        assertReachableInTier(IndustryBalance.SPEED2_UNLOCK_LEVEL, 2, "speed II");
        assertReachableInTier(IndustryBalance.ENERGY_UNLOCK_LEVEL, 2, "energy");
        assertReachableInTier(IndustryBalance.EFFICIENCY2_UNLOCK_LEVEL, 2, "efficiency II");
        assertReachableInTier(IndustryBalance.YIELD2_UNLOCK_LEVEL, 2, "yield II");
        assertReachableInTier(IndustryBalance.LURE2_UNLOCK_LEVEL, 2, "lure II");
        assertReachableInTier(IndustryBalance.LUCK2_UNLOCK_LEVEL, 2, "luck II");
    }

    @Test
    void eachTierUnlocksSpeedThenEfficiencyThenYield() {
        assertTrue(IndustryBalance.SPEED_UNLOCK_LEVEL < IndustryBalance.EFFICIENCY_UNLOCK_LEVEL);
        assertTrue(IndustryBalance.EFFICIENCY_UNLOCK_LEVEL < IndustryBalance.YIELD_UNLOCK_LEVEL);
        assertTrue(IndustryBalance.SPEED2_UNLOCK_LEVEL < IndustryBalance.EFFICIENCY2_UNLOCK_LEVEL);
        assertTrue(IndustryBalance.EFFICIENCY2_UNLOCK_LEVEL < IndustryBalance.YIELD2_UNLOCK_LEVEL);
    }

    @Test
    void efficiencyGrowsWithEveryPointAndClampsPastTheTable() {
        assertEquals(1000, IndustryBalance.efficiencyPermille(0), "no module, no discount");
        for (int points = 1; points <= 8; points++) {
            assertTrue(IndustryBalance.efficiencyPermille(points) < IndustryBalance.efficiencyPermille(points - 1),
                    points + " points cost no less than " + (points - 1));
            assertTrue(IndustryBalance.efficiencyPermille(points) > 0);
        }
        assertEquals(IndustryBalance.efficiencyPermille(8), IndustryBalance.efficiencyPermille(50));
    }

    @Test
    void aGrindGivesTheRecipeCountPlusAQuarterPerCountedPoint() {
        assertEquals(2 * 4, IndustryBalance.grindQuarters(2, true, 0), "two dusts, no module");
        assertEquals(3 * 4, IndustryBalance.grindQuarters(2, true, 2), "tier 1 cap: three dusts");
        assertEquals(6 * 4, IndustryBalance.grindQuarters(2, true, 8), "last tier cap: six dusts");
        assertEquals(39 * 4, IndustryBalance.grindQuarters(13, true, 8), "lapis at the last tier");
    }

    @Test
    void offTheNetworkAGrindGivesExactlyHalfAndNeverLessThanOne() {
        assertEquals(13 * 4, 2 * IndustryBalance.grindQuarters(13, false, 0), "two lapis ores give 13 lapis");
        assertEquals(7 * 4, 2 * IndustryBalance.grindQuarters(7, false, 0), "two copper ores give 7 dusts");
        assertEquals(4, IndustryBalance.grindQuarters(2, false, 0), "two dusts fall back to one");
        assertEquals(4, IndustryBalance.grindQuarters(1, false, 0), "cobblestone to gravel stays one for one");
        assertEquals(IndustryBalance.grindQuarters(2, false, 0), IndustryBalance.grindQuarters(2, false, 8),
                "modules sleep off the network");
    }

    @Test
    void zoneMachinesEarnLessXpThanTheGrinder() {
        // The reduced-XP rule of zone machines: a fraction of a grind, never more.
        for (int xp : new int[] {IndustryBalance.HARVEST_BASE_XP, IndustryBalance.FELL_BASE_XP,
                IndustryBalance.BREED_BASE_XP, IndustryBalance.CREATURE_HARVEST_BASE_XP,
                IndustryBalance.KILL_BASE_XP, IndustryBalance.SPAWN_BASE_XP, IndustryBalance.CATCH_BASE_XP}) {
            assertTrue(xp > 0 && xp < IndustryBalance.GRIND_BASE_XP, "zone XP " + xp);
        }
    }
}
