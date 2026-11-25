package io.lakio.krios.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class EnergyBufferTest {

    @Test
    void insertStopsAtCapacity() {
        EnergyBuffer buffer = new EnergyBuffer(1000, 100);
        assertEquals(600, buffer.insert(600));
        assertEquals(400, buffer.insert(600));
        assertTrue(buffer.isFull());
        assertEquals(0, buffer.insert(1));
    }

    @Test
    void extractionIsCappedPerCall() {
        EnergyBuffer buffer = new EnergyBuffer(1000, 100);
        buffer.insert(1000);
        assertEquals(100, buffer.extract(500));
        assertEquals(900, buffer.getStored());
    }

    @Test
    void extractionNeverGoesBelowZero() {
        EnergyBuffer buffer = new EnergyBuffer(1000, 100);
        buffer.insert(30);
        assertEquals(30, buffer.extract(100));
        assertEquals(0, buffer.extract(100));
    }

    @Test
    void consumptionIsAllOrNothingAndIgnoresTheExtractCap() {
        EnergyBuffer buffer = new EnergyBuffer(1000, 100);
        buffer.insert(500);
        assertTrue(buffer.consume(400), "a machine's own use is not an external extraction");
        assertEquals(100, buffer.getStored());
        assertFalse(buffer.consume(101));
        assertEquals(100, buffer.getStored(), "a refused consumption takes nothing");
    }

    @Test
    void aRestoreIsClamped() {
        EnergyBuffer buffer = new EnergyBuffer(1000, 100);
        buffer.setStored(5000);
        assertEquals(1000, buffer.getStored());
        buffer.setStored(-5);
        assertEquals(0, buffer.getStored());
    }

    @Test
    void aSaveLoadsBackAndASmallerBufferClampsIt() {
        EnergyBuffer buffer = new EnergyBuffer(1000, 100);
        buffer.insert(750);
        CompoundTag tag = new CompoundTag();
        buffer.save(tag);

        EnergyBuffer same = new EnergyBuffer(1000, 100);
        same.load(tag);
        assertEquals(750, same.getStored());

        EnergyBuffer smaller = new EnergyBuffer(500, 100);
        smaller.load(tag);
        assertEquals(500, smaller.getStored());
    }
}
