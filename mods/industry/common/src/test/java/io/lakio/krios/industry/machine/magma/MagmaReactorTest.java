package io.lakio.krios.industry.machine.magma;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.reactor.ThermalReactorBlockEntity;
import org.junit.jupiter.api.Test;

class MagmaReactorTest {

    /** Vanilla's burn time of a lava bucket, the Thermal Reactor's lava. */
    private static final int LAVA_BUCKET_BURN_TICKS = 20_000;

    @Test
    void lavaGivesTwiceWhatTheThermalReactorGets() {
        int thermalPerBucket = ThermalReactorBlockEntity.GENERATION_PER_TICK * LAVA_BUCKET_BURN_TICKS;
        assertEquals(320_000, thermalPerBucket);
        assertEquals(2 * thermalPerBucket, MagmaReactorBlockEntity.ENERGY_PER_MB * LavaTank.BUCKET);
    }

    @Test
    void aBurnCycleDeliversExactlyItsLava() {
        // Rate x ticks = lava x value, at the base rate: no energy lost to rounding.
        assertEquals(MagmaReactorBlockEntity.ENERGY_PER_MB,
                MagmaReactorBlockEntity.GENERATION_PER_TICK * MagmaReactorBlockEntity.BURN_TICKS_PER_MB);
    }

    @Test
    void atTierTwoItDeliversTwiceAThermalReactorTierTwo() {
        int magma = MagmaReactorBlockEntity.GENERATION_PER_TICK * MachineTiers.speedFactor(2);
        int thermal = ThermalReactorBlockEntity.GENERATION_PER_TICK * MachineTiers.speedFactor(2);
        assertEquals(64, magma);
        assertEquals(2 * thermal, magma);
    }

    @Test
    void theTankHoldsSixteenBucketsAtTierTwo() {
        assertEquals(16 * LavaTank.BUCKET, MagmaReactorBlockEntity.TANK_PER_TIER * 2);
        assertEquals(200_000, MagmaReactorBlockEntity.CAPACITY_PER_TIER * 2);
    }

    @Test
    void theTankFillsAndDrainsWithinItsBounds() {
        LavaTank tank = new LavaTank(1_500);
        assertEquals(1_000, tank.fill(1_000, false));
        assertEquals(500, tank.fill(1_000, true), "a simulated fill takes only the room left");
        assertEquals(1_000, tank.getStored(), "a simulated fill changes nothing");
        assertEquals(500, tank.fill(1_000, false));
        assertEquals(0, tank.room());
        assertEquals(1_500, tank.drain(2_000, false));
        assertEquals(0, tank.drain(10, false));
        tank.setStored(9_999);
        assertEquals(1_500, tank.getStored(), "a loaded amount is clamped to the capacity");
    }
}
