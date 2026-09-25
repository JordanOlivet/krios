package io.lakio.krios.industry.network;

/**
 * What can cross a machine face. FLUID is reserved (bits allocated, no
 * transport yet); DATA gates diagnostic/XP exposure later.
 */
public enum Flow {
    ENERGY,
    ITEM,
    FLUID,
    DATA;

    public static final Flow[] VALUES = values();
}
