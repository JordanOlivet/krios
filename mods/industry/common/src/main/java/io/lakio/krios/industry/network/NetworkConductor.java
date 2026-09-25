package io.lakio.krios.industry.network;

/**
 * Marker for blocks that propagate the network by adjacency (machines, the
 * core, casings, cables). The graph walk visits positions whose block
 * implements this; blocks without a block entity must also call
 * {@link NetworkManager#conductorChanged} from their place/remove hooks
 * (ignoring same-block property flips). Pure marker — the engine never
 * imports mod blocks (ADR-013).
 */
public interface NetworkConductor {
}
