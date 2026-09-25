package io.lakio.krios.industry.network;

import net.minecraft.core.Direction;

/**
 * What a node's faces could do, for contact auto-configuration. Capability
 * declaration only — whether a flow is currently allowed is the
 * {@link FaceConfig}'s job.
 */
public interface FlowPorts {

    /** This face could push {@code flow} out. */
    boolean canOffer(Direction face, Flow flow);

    /** This face could take {@code flow} in. */
    boolean canAccept(Direction face, Flow flow);

    FaceConfig faceConfig();

    /** Called after auto-config touched the face config (persist it). */
    default void faceConfigDirty() {
    }
}
