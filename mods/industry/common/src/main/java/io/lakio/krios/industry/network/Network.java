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
    private final int size;
    private boolean dirty;

    Network(List<NetworkNode> members, List<NetworkNode> controllers, int size) {
        this.members = Collections.unmodifiableList(members);
        this.controllers = Collections.unmodifiableList(controllers);
        this.size = size;
    }

    public List<NetworkNode> members() {
        return members;
    }

    /** Component size in blocks, conductors included (diagnostics). */
    public int size() {
        return size;
    }

    /** Sum of the pool's producer buffers (diagnostics). */
    public int energyStored() {
        int total = 0;
        for (NetworkNode member : members) {
            if (member instanceof EnergyPort port) {
                total += port.storedNetworkEnergy();
            }
        }
        return total;
    }

    public int energyCapacity() {
        int total = 0;
        for (NetworkNode member : members) {
            if (member instanceof EnergyPort port) {
                total += port.networkEnergyCapacity();
            }
        }
        return total;
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

    /**
     * Pooled energy distribution: offers up to {@code amount} to members
     * implementing {@link EnergySink}, returns what was accepted. Two
     * serving tiers ({@link EnergySink#sinkPriority()}): tier 0 (working
     * machines) is fully served before tier 1 (idle buffers) sees any
     * surplus. Within a tier, a fair-share sweep precedes a greedy leftover
     * sweep so no single sink monopolizes the flow. Allocation-free — this
     * runs on producer ticks (ADR-012). Only a valid network transfers.
     */
    public int offerEnergy(int amount, NetworkNode source) {
        if (!isValid() || amount <= 0) {
            return 0;
        }
        int accepted = 0;
        // Rotating start index: the leftover sweep otherwise concentrates
        // the whole net surplus on the FIRST non-full sink every tick
        // (observed as buffers recharging strictly one after another).
        int memberCount = members.size();
        int start = (distributionCursor++ & 0x7FFFFFFF) % memberCount;
        for (int priority = 0; priority <= 1 && accepted < amount; priority++) {
            int sinkCount = 0;
            for (NetworkNode member : members) {
                if (member != source && member instanceof EnergySink sink
                        && normalizedPriority(sink) == priority) {
                    sinkCount++;
                }
            }
            if (sinkCount == 0) {
                continue;
            }
            // Fair-share sweep.
            int share = Math.max(1, (amount - accepted) / sinkCount);
            for (int i = 0; i < memberCount && accepted < amount; i++) {
                NetworkNode member = members.get((start + i) % memberCount);
                if (member != source && member instanceof EnergySink sink
                        && normalizedPriority(sink) == priority) {
                    accepted += sink.insertNetworkEnergy(Math.min(share, amount - accepted));
                }
            }
            // Leftover sweep, same rotated order.
            for (int i = 0; i < memberCount && accepted < amount; i++) {
                NetworkNode member = members.get((start + i) % memberCount);
                if (member != source && member instanceof EnergySink sink
                        && normalizedPriority(sink) == priority) {
                    accepted += sink.insertNetworkEnergy(amount - accepted);
                }
            }
        }
        return accepted;
    }

    /** Round-robin cursor for offerEnergy; runtime-only, never persisted. */
    private int distributionCursor;

    private static int normalizedPriority(EnergySink sink) {
        return sink.sinkPriority() <= 0 ? 0 : 1;
    }

    void markDirty() {
        dirty = true;
    }

    boolean isDirty() {
        return dirty;
    }
}
