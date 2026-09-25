package io.lakio.krios.industry.network;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * One connected component of the graph — an immutable snapshot, recomputed
 * event-driven by {@link NetworkManager} and never persisted: the graph
 * rebuilds from world state (ADR-013).
 */
public final class Network {

    private final List<NetworkNode> members;
    private final List<NetworkNode> controllers;
    private boolean dirty;

    Network(List<NetworkNode> members, List<NetworkNode> controllers) {
        this.members = Collections.unmodifiableList(members);
        this.controllers = Collections.unmodifiableList(controllers);
    }

    public List<NetworkNode> members() {
        return members;
    }

    public List<NetworkNode> controllers() {
        return controllers;
    }

    /** Exactly one active controller: the network is up. */
    public boolean isValid() {
        return controllers.size() == 1;
    }

    /** Two or more active controllers: the whole component is down. */
    public boolean isFault() {
        return controllers.size() > 1;
    }

    /** The governing controller, or null while the network is not valid. */
    @Nullable
    public NetworkNode controller() {
        return isValid() ? controllers.get(0) : null;
    }

    /**
     * Pooled energy transfer: gathers up to {@code amount} from members
     * implementing {@link EnergyPort}. Only a valid network transfers —
     * orphan or faulted components move nothing.
     */
    public int requestEnergy(int amount, NetworkNode requester) {
        if (!isValid() || amount <= 0) {
            return 0;
        }
        int gathered = 0;
        for (NetworkNode member : members) {
            if (member != requester && member instanceof EnergyPort port) {
                gathered += port.extractNetworkEnergy(amount - gathered);
                if (gathered >= amount) {
                    break;
                }
            }
        }
        return gathered;
    }

    void markDirty() {
        dirty = true;
    }

    boolean isDirty() {
        return dirty;
    }
}
