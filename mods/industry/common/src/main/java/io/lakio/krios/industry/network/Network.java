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

    void markDirty() {
        dirty = true;
    }

    boolean isDirty() {
        return dirty;
    }
}
