package io.lakio.krios.core.network;

/**
 * What can cross a machine face. FLUID has no network transport: it is the
 * contract of a machine's face with other mods' pipes and touching blocks
 * (the Magma Reactor's lava, ADR-039); DATA gates diagnostic/XP exposure later.
 */
public enum Flow {
    ENERGY,
    ITEM,
    FLUID,
    DATA;

    public static final Flow[] VALUES = values();
}
