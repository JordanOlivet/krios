package io.lakio.krios.industry.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lakio.krios.core.koios.KoiosBalance;
import org.junit.jupiter.api.Test;

class MachineTiersTest {

    @Test
    void eachTierDoublesSpeedAndColumns() {
        for (int tier = 1; tier <= KoiosBalance.MAX_TIER; tier++) {
            assertEquals(1 << (tier - 1), MachineTiers.speedFactor(tier));
            assertEquals(1 << (tier - 1), MachineTiers.processSlots(tier));
        }
    }

    @Test
    void theParkRatioHoldsAtEveryTier() {
        // Generation and consumption double together: what one reactor feeds stays the same.
        for (int tier = 2; tier <= KoiosBalance.MAX_TIER; tier++) {
            assertEquals(MachineTiers.speedFactor(tier) / MachineTiers.speedFactor(tier - 1),
                    MachineTiers.processSlots(tier) / MachineTiers.processSlots(tier - 1));
        }
    }

    @Test
    void theGrinderYieldLadderNeverDrops() {
        assertEquals(2, MachineTiers.yieldNum(1));
        for (int tier = 2; tier <= KoiosBalance.MAX_TIER; tier++) {
            assertTrue(MachineTiers.yieldNum(tier) >= MachineTiers.yieldNum(tier - 1), "tier " + tier);
        }
    }

    @Test
    void zonesGrowRoughlyDoubleInAreaAndStayOddAroundTheMachine() {
        assertEquals(9, MachineTiers.zoneEdge(1), "what one water source hydrates");
        for (int tier = 1; tier <= KoiosBalance.MAX_TIER; tier++) {
            int edge = MachineTiers.zoneEdge(tier);
            assertEquals(1, edge % 2, "tier " + tier + ": the zone is centred on a column");
            if (tier > 1) {
                int previous = MachineTiers.zoneEdge(tier - 1);
                double ratio = (double) (edge * edge) / (previous * previous);
                assertTrue(ratio > 1.8 && ratio < 2.3, "tier " + tier + " area ratio " + ratio);
            }
        }
    }

    @Test
    void populationCapsDouble() {
        for (int tier = 2; tier <= KoiosBalance.MAX_TIER; tier++) {
            assertEquals(2 * MachineTiers.populationCap(tier - 1), MachineTiers.populationCap(tier));
        }
    }

    @Test
    void offTheNetworkAMachineWorksAtTierOne() {
        for (int tier = 1; tier <= KoiosBalance.MAX_TIER; tier++) {
            assertEquals(1, MachineTiers.effectiveTier(null, tier));
        }
    }
}
