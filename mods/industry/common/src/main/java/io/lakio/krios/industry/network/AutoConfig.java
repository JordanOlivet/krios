package io.lakio.krios.industry.network;

import net.minecraft.core.Direction;

/**
 * Contact auto-configuration (design, 2026-09-25): a new contact between
 * two non-manual faces renegotiates them from scratch — the player never
 * configured anything, so a different neighbor gets a fresh negotiation.
 * Each flow with exactly one workable direction configures both faces as
 * the complementary pair. A symmetric pair (both sides could send and
 * receive the same flow) configures nothing — both faces stay virgin and
 * carry a hint for the Calibrator. Manual config is sacred: if either
 * face is manual, nothing happens.
 */
public final class AutoConfig {

    /** {@code direction} points from {@code self} toward {@code other}. */
    public static void onContact(FlowPorts self, FlowPorts other, Direction direction) {
        Direction selfFace = direction;
        Direction otherFace = direction.getOpposite();
        if (self.faceConfig().isManual(selfFace) || other.faceConfig().isManual(otherFace)) {
            return;
        }
        self.faceConfig().resetAuto(selfFace);
        other.faceConfig().resetAuto(otherFace);
        for (Flow flow : Flow.VALUES) {
            boolean forward = self.canOffer(selfFace, flow) && other.canAccept(otherFace, flow);
            boolean backward = other.canOffer(otherFace, flow) && self.canAccept(selfFace, flow);
            if (forward && backward) {
                self.faceConfig().setHint(selfFace);
                other.faceConfig().setHint(otherFace);
            } else if (forward) {
                self.faceConfig().setAuto(selfFace, flow, true);
                other.faceConfig().setAuto(otherFace, flow, false);
            } else if (backward) {
                other.faceConfig().setAuto(otherFace, flow, true);
                self.faceConfig().setAuto(selfFace, flow, false);
            }
        }
        self.faceConfigDirty();
        other.faceConfigDirty();
    }

    private AutoConfig() {
    }
}
