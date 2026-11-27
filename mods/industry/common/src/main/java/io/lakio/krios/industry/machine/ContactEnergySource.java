package io.lakio.krios.industry.machine;

import net.minecraft.core.Direction;

/**
 * A generator a touching machine pulls from off the network (the v0 contact
 * transport): the Thermal and Magma Reactors. A pull also relights a
 * generator paused on a full buffer, which is why consumers pull instead of
 * waiting for a push.
 */
public interface ContactEnergySource {

    /** The face contract for external extraction, from this generator's side. */
    boolean allowsEnergyExtract(Direction side);

    /** Takes up to {@code max} energy; returns what came out. */
    int extractNetworkEnergy(int max);
}
