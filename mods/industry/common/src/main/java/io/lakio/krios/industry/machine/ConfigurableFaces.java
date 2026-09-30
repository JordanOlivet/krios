package io.lakio.krios.industry.machine;

import io.lakio.krios.industry.network.FlowPorts;

/**
 * A machine whose per-face contract the player can read: the terminal's
 * hologram panel and the Calibrator's in-world overlay both derive their
 * flow columns from the same supported-bits mask that seeds the manual
 * presets, so the views can never diverge from the server contract.
 */
public interface ConfigurableFaces extends FlowPorts {

    /** Union of this machine's supported {@code FaceConfig} flow bits (in and out). */
    short supportedFaceBits();
}
