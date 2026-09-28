package io.lakio.krios.industry.client;

import java.util.List;

/**
 * Blocks whose model carries a fullbright "_glow" overlay sprite (the vein
 * pixels, derived by tools/gen_ore_glow.py). The emissive mechanism itself
 * is per loader — FRAPI quad transform on Fabric, baked-model wrapper on
 * NeoForge — this is the shared list of block ids plus the sprite naming
 * contract both sides key on.
 */
public final class EmissiveOverlays {

    public static final String GLOW_SUFFIX = "_glow";

    /** Block ids (path only) whose baked models get the emissive wrapper. */
    public static final List<String> BLOCKS = List.of(
            "chronite_ore",
            "deepslate_chronite_ore",
            "neurite_ore",
            "deepslate_neurite_ore");

    private EmissiveOverlays() {
    }
}
