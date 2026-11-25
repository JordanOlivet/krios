package io.lakio.krios.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class FaceConfigTest {

    @Test
    void everyFaceStartsVirginAndAllowsNothingExplicitly() {
        FaceConfig config = new FaceConfig();
        for (Direction face : Direction.values()) {
            assertTrue(config.isVirgin(face));
            assertFalse(config.isManual(face));
            for (Flow flow : Flow.VALUES) {
                assertFalse(config.allows(face, flow, true));
                assertFalse(config.allows(face, flow, false));
            }
        }
    }

    @Test
    void autoConfigRecordsTheNegotiatedFlowOnly() {
        FaceConfig config = new FaceConfig();
        config.setAuto(Direction.NORTH, Flow.ENERGY, true);
        assertFalse(config.isVirgin(Direction.NORTH));
        assertFalse(config.isManual(Direction.NORTH));
        assertTrue(config.allows(Direction.NORTH, Flow.ENERGY, true));
        assertFalse(config.allows(Direction.NORTH, Flow.ENERGY, false));
        assertTrue(config.isVirgin(Direction.SOUTH), "other faces are untouched");
    }

    @Test
    void autoConfigNeverOverridesTheManualContract() {
        FaceConfig config = new FaceConfig();
        config.enterManual(Direction.UP, FaceConfig.flowBits(Flow.ITEM, false));
        config.setAuto(Direction.UP, Flow.ENERGY, true);
        config.setHint(Direction.UP);
        config.resetAuto(Direction.UP);
        assertTrue(config.isManual(Direction.UP));
        assertTrue(config.allows(Direction.UP, Flow.ITEM, false));
        assertFalse(config.allows(Direction.UP, Flow.ENERGY, true));
        assertFalse(config.hasHint(Direction.UP));
    }

    @Test
    void aNewContactRenegotiatesAnAutoFace() {
        FaceConfig config = new FaceConfig();
        config.setAuto(Direction.EAST, Flow.ITEM, true);
        config.resetAuto(Direction.EAST);
        assertTrue(config.isVirgin(Direction.EAST));
        assertFalse(config.allows(Direction.EAST, Flow.ITEM, true));
    }

    @Test
    void aHintKeepsTheFaceVirginUntilThePlayerDecides() {
        FaceConfig config = new FaceConfig();
        config.setHint(Direction.WEST);
        assertTrue(config.hasHint(Direction.WEST));
        assertTrue(config.isVirgin(Direction.WEST));
        config.toggleManual(Direction.WEST, Flow.ITEM, true);
        assertFalse(config.hasHint(Direction.WEST), "a manual edit answers the hint");
        assertTrue(config.isManual(Direction.WEST));
        assertTrue(config.allows(Direction.WEST, Flow.ITEM, true));
    }

    @Test
    void toggleFlipsOneBitAndStaysManual() {
        FaceConfig config = new FaceConfig();
        config.enterManual(Direction.DOWN, (short) 0);
        config.toggleManual(Direction.DOWN, Flow.ENERGY, false);
        assertTrue(config.allows(Direction.DOWN, Flow.ENERGY, false));
        config.toggleManual(Direction.DOWN, Flow.ENERGY, false);
        assertFalse(config.allows(Direction.DOWN, Flow.ENERGY, false));
        assertTrue(config.isManual(Direction.DOWN), "an empty contract is still the player's");
    }

    @Test
    void thePlayersResetGoesBackToVirgin() {
        FaceConfig config = new FaceConfig();
        config.enterManual(Direction.NORTH, FaceConfig.flowBits(Flow.ITEM, true));
        config.resetToVirgin(Direction.NORTH);
        assertTrue(config.isVirgin(Direction.NORTH));
        assertFalse(config.isManual(Direction.NORTH));
    }

    @Test
    void theStaticDecodersReadTheSyncedBits() {
        FaceConfig config = new FaceConfig();
        config.enterManual(Direction.SOUTH, FaceConfig.flowBits(Flow.ITEM, false));
        short bits = config.bits(Direction.SOUTH);
        assertTrue(FaceConfig.isManualBits(bits));
        assertTrue(FaceConfig.isConfiguredBits(bits));
        assertFalse(FaceConfig.hasHintBits(bits));
        assertTrue(FaceConfig.allowsBits(bits, Flow.ITEM, false));
        assertFalse(FaceConfig.allowsBits(bits, Flow.ITEM, true));
    }

    @Test
    void everyFlowAndDirectionHasItsOwnBit() {
        int seen = 0;
        for (Flow flow : Flow.VALUES) {
            for (boolean out : new boolean[] {false, true}) {
                int bit = FaceConfig.flowBits(flow, out);
                assertEquals(1, Integer.bitCount(bit));
                assertEquals(0, seen & bit, flow + (out ? " out" : " in") + " shares a bit");
                seen |= bit;
            }
        }
    }

    @Test
    void aSaveLoadsBackFaceByFace() {
        FaceConfig config = new FaceConfig();
        config.enterManual(Direction.UP, FaceConfig.flowBits(Flow.ITEM, false));
        config.setAuto(Direction.NORTH, Flow.ENERGY, true);
        config.setHint(Direction.EAST);
        CompoundTag tag = new CompoundTag();
        config.save(tag);

        FaceConfig loaded = new FaceConfig();
        loaded.load(tag);
        for (Direction face : Direction.values()) {
            assertEquals(config.bits(face), loaded.bits(face), face.getName());
        }
    }

    @Test
    void anAllVirginConfigSavesNothingAndLoadsVirgin() {
        CompoundTag tag = new CompoundTag();
        new FaceConfig().save(tag);
        assertFalse(tag.contains("Faces"));
        FaceConfig loaded = new FaceConfig();
        loaded.load(tag);
        assertTrue(loaded.isVirgin(Direction.UP));
    }
}
