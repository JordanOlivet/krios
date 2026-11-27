package io.lakio.krios.industry.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void eachTierAllowsTwoMoreYieldPoints() {
        // ADR-038: 2 / 4 / 6 / 8 points, so at most three times a recipe's count at the last tier.
        for (int tier = 1; tier <= KoiosBalance.MAX_TIER; tier++) {
            assertEquals(2 * tier, MachineTiers.yieldPointCap(tier), "tier " + tier);
        }
        assertEquals(8, MachineTiers.yieldPointCap(KoiosBalance.MAX_TIER));
    }

    @Test
    void yieldPointsPastTheCapDoNotCount() {
        assertEquals(0, MachineTiers.countedYieldPoints(0, 1));
        assertEquals(2, MachineTiers.countedYieldPoints(2, 1));
        assertEquals(2, MachineTiers.countedYieldPoints(8, 1), "four Mk II in a tier 1 bay count two");
        assertEquals(4, MachineTiers.countedYieldPoints(8, 2));
        assertEquals(3, MachineTiers.countedYieldPoints(3, 4));
    }

    @Test
    void aYieldModuleFitsOnlyUnderTheCap() {
        assertTrue(MachineTiers.yieldModuleFits(0, 2, 1), "one Mk II fills a tier 1 bay");
        assertTrue(MachineTiers.yieldModuleFits(1, 1, 1), "two Mk I fill a tier 1 bay");
        assertFalse(MachineTiers.yieldModuleFits(2, 1, 1), "a third point at tier 1");
        assertFalse(MachineTiers.yieldModuleFits(1, 2, 1), "a Mk II beside a Mk I at tier 1");
        assertTrue(MachineTiers.yieldModuleFits(2, 2, 2));
        assertFalse(MachineTiers.yieldModuleFits(4, 1, 2));
    }

    @Test
    void eachTierAllowsOneMoreEnergyPoint() {
        // ADR-039: 1 / 2 / 3 / 4 points, so at most twice a fuel's energy at the last tier.
        for (int tier = 1; tier <= KoiosBalance.MAX_TIER; tier++) {
            assertEquals(tier, MachineTiers.energyPointCap(tier), "tier " + tier);
        }
    }

    @Test
    void energyPointsPastTheCapDoNotCount() {
        assertEquals(1, MachineTiers.countedEnergyPoints(2, 1), "two Energy modules off a tier 2 core count one");
        assertEquals(2, MachineTiers.countedEnergyPoints(4, 2), "four in a tier 2 Magma Reactor count two");
        assertEquals(2, MachineTiers.countedEnergyPoints(2, 2), "the Thermal Reactor's two slots stay whole");
        assertEquals(3, MachineTiers.countedEnergyPoints(3, 4));
    }

    @Test
    void anEnergyModuleFitsOnlyUnderTheCap() {
        assertTrue(MachineTiers.energyModuleFits(1, 1, 2), "a second point at tier 2");
        assertFalse(MachineTiers.energyModuleFits(2, 1, 2), "a third point at tier 2");
        assertFalse(MachineTiers.energyModuleFits(1, 1, 1), "a second point at tier 1");
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
