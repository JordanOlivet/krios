package io.lakio.krios.industry.network;

import net.minecraft.core.Direction;

/**
 * Contact auto-configuration (design, 2026-09-25): when two nodes touch,
 * each flow with exactly one workable direction configures both faces as
 * the complementary pair. A symmetric pair (both sides could send and
 * receive the same flow) configures nothing — both faces stay virgin and
 * carry a hint for the Calibrator. First contact only; manual config is
 * sacred and never overridden.
 */
public final class AutoConfig {

    /** {@code direction} points from {@code self} toward {@code other}. */
    public static void onContact(FlowPorts self, FlowPorts other, Direction direction) {
        Direction selfFace = direction;
        Direction otherFace = direction.getOpposite();
        boolean selfChanged = false;
        boolean otherChanged = false;
        for (Flow flow : Flow.VALUES) {
            boolean forward = self.canOffer(selfFace, flow) && other.canAccept(otherFace, flow);
            boolean backward = other.canOffer(otherFace, flow) && self.canAccept(selfFace, flow);
            if (forward && backward) {
                self.faceConfig().setHint(selfFace);
                other.faceConfig().setHint(otherFace);
                selfChanged = otherChanged = true;
            } else if (forward && self.faceConfig().isVirgin(selfFace)
                    && other.faceConfig().isVirgin(otherFace)) {
                self.faceConfig().setAuto(selfFace, flow, true);
                other.faceConfig().setAuto(otherFace, flow, false);
                selfChanged = otherChanged = true;
            } else if (backward && self.faceConfig().isVirgin(selfFace)
                    && other.faceConfig().isVirgin(otherFace)) {
                other.faceConfig().setAuto(otherFace, flow, true);
                self.faceConfig().setAuto(selfFace, flow, false);
                selfChanged = otherChanged = true;
            }
        }
        if (selfChanged) {
            self.faceConfigDirty();
        }
        if (otherChanged) {
            other.faceConfigDirty();
        }
    }

    private AutoConfig() {
    }
}
