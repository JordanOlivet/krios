package io.lakio.krios.industry.network;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * Per-face interaction contract of a machine. Two regimes (design):
 * MANUAL config is the face's complete contract — it gates network flows
 * and third-party neighbors alike and is never touched by auto-config.
 * AUTO config only records the flow direction negotiated on contact; it
 * keeps vanilla third-party behavior and is renegotiated from scratch on
 * every new contact. Bit layout per face: two bits per {@link Flow}
 * (in/out), then CONFIGURED, MANUAL and HINT. Owned and persisted by the
 * block entity — the engine never saves anything itself (ADR-013).
 */
public final class FaceConfig {

    private static final int CONFIGURED = 1 << 8;
    private static final int MANUAL = 1 << 9;
    private static final int HINT = 1 << 10;

    private final short[] faces = new short[6];

    private static int flowBit(Flow flow, boolean out) {
        return 1 << (flow.ordinal() * 2 + (out ? 1 : 0));
    }

    /** Never configured (auto or manual): vanilla behavior applies. */
    public boolean isVirgin(Direction face) {
        return (faces[face.get3DDataValue()] & CONFIGURED) == 0;
    }

    public boolean isManual(Direction face) {
        return (faces[face.get3DDataValue()] & MANUAL) != 0;
    }

    /** Symmetric-contact ambiguity: nothing was configured, the Calibrator hints. */
    public boolean hasHint(Direction face) {
        return (faces[face.get3DDataValue()] & HINT) != 0;
    }

    /** Configured faces only — callers resolve virgin faces to their vanilla default. */
    public boolean allows(Direction face, Flow flow, boolean out) {
        return (faces[face.get3DDataValue()] & flowBit(flow, out)) != 0;
    }

    /** Contact auto-config: never over manual config. */
    public void setAuto(Direction face, Flow flow, boolean out) {
        int index = face.get3DDataValue();
        if ((faces[index] & MANUAL) == 0) {
            faces[index] |= (short) (flowBit(flow, out) | CONFIGURED);
        }
    }

    /** Flags a symmetric pair; the face stays virgin (explicit beats magic). */
    public void setHint(Direction face) {
        int index = face.get3DDataValue();
        if ((faces[index] & MANUAL) == 0) {
            faces[index] |= HINT;
        }
    }

    /**
     * Clears auto state ahead of a renegotiation (new contact = fresh
     * negotiation, the player never configured anything). Manual faces are
     * untouched.
     */
    public void resetAuto(Direction face) {
        int index = face.get3DDataValue();
        if ((faces[index] & MANUAL) == 0) {
            faces[index] = 0;
        }
    }

    // --- UI/sync access: faces sync to menus as raw shorts (they fit the
    // 16-bit ContainerData budget) and the client decodes them with the
    // static helpers below. ---

    public short bits(Direction face) {
        return faces[face.get3DDataValue()];
    }

    /** Back to vanilla: unconditional, this is the player's own reset. */
    public void resetToVirgin(Direction face) {
        faces[face.get3DDataValue()] = 0;
    }

    /** Enters manual mode with the given flow bits as the full contract. */
    public void enterManual(Direction face, short flowBits) {
        faces[face.get3DDataValue()] = (short) (flowBits | CONFIGURED | MANUAL);
    }

    /** Flips one manual flow bit, keeping the face manual. */
    public void toggleManual(Direction face, Flow flow, boolean out) {
        int index = face.get3DDataValue();
        faces[index] ^= (short) flowBit(flow, out);
        faces[index] |= CONFIGURED | MANUAL;
        faces[index] &= (short) ~HINT;
    }

    public static short flowBits(Flow flow, boolean out) {
        return (short) flowBit(flow, out);
    }

    public static boolean isManualBits(int bits) {
        return (bits & MANUAL) != 0;
    }

    public static boolean hasHintBits(int bits) {
        return (bits & HINT) != 0;
    }

    public static boolean isConfiguredBits(int bits) {
        return (bits & CONFIGURED) != 0;
    }

    public static boolean allowsBits(int bits, Flow flow, boolean out) {
        return (bits & flowBit(flow, out)) != 0;
    }

    public void setManual(Direction face, Flow flow, boolean out, boolean allowed) {
        int index = face.get3DDataValue();
        if (allowed) {
            faces[index] |= (short) flowBit(flow, out);
        } else {
            faces[index] &= (short) ~flowBit(flow, out);
        }
        faces[index] |= CONFIGURED | MANUAL;
        faces[index] &= (short) ~HINT;
    }

    public void save(CompoundTag tag) {
        int[] packed = new int[6];
        boolean any = false;
        for (int i = 0; i < 6; i++) {
            packed[i] = faces[i];
            any |= faces[i] != 0;
        }
        if (any) {
            tag.putIntArray("Faces", packed);
        }
    }

    public void load(CompoundTag tag) {
        int[] packed = tag.getIntArray("Faces");
        for (int i = 0; i < 6; i++) {
            faces[i] = packed.length == 6 ? (short) packed[i] : 0;
        }
    }
}
