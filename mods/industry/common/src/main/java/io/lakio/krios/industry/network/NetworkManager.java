package io.lakio.krios.industry.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The network graph, per dimension. Server thread only, zero ticking: the
 * graph reacts to block/node events and recomputes only the touched
 * connected component (bounded flood fill over {@link NetworkConductor}
 * blocks). Components are cached per position and invalidated by events;
 * queries recompute lazily when the eager pass could not run (e.g. during
 * chunk load). Nothing here is ever persisted (ADR-013) and nothing here
 * knows about mod content — controllers, balance and behavior live in the
 * consuming mod.
 */
public final class NetworkManager {

    private static final Logger LOG = LoggerFactory.getLogger("krios-network");
    /** Safety valve on component size; beyond it the walk stops expanding. */
    private static final int MAX_COMPONENT_BLOCKS = 4096;
    private static final Direction[] DIRECTIONS = Direction.values();

    private static final Map<ResourceKey<Level>, DimensionGraph> GRAPHS = new HashMap<>();

    private static final class DimensionGraph {
        /** Registered (BE-backed) nodes by position. */
        final Map<BlockPos, NetworkNode> nodes = new HashMap<>();
        /** Every position of a computed component maps to its network. */
        final Map<BlockPos, Network> byPos = new HashMap<>();
    }

    // --- Node/block lifecycle events ---

    public static void addNode(NetworkNode node) {
        Level level = node.networkLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        DimensionGraph graph = graph(level);
        BlockPos pos = node.networkPos().immutable();
        graph.nodes.put(pos, node);
        // Contact auto-config runs on node placement, before the graph
        // recompute — it only touches the two touching faces.
        if (node instanceof FlowPorts self) {
            for (Direction direction : DIRECTIONS) {
                if (graph.nodes.get(pos.relative(direction)) instanceof FlowPorts other) {
                    AutoConfig.onContact(self, other, direction);
                }
            }
        }
        invalidateAround(graph, pos);
        recompute(level, graph, pos);
    }

    public static void removeNode(NetworkNode node) {
        Level level = node.networkLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        DimensionGraph graph = GRAPHS.get(level.dimension());
        if (graph == null) {
            return;
        }
        BlockPos pos = node.networkPos();
        graph.nodes.remove(pos);
        Network old = graph.byPos.remove(pos);
        if (old != null) {
            old.markDirty();
        }
        invalidateAround(graph, pos);
        recomputeNeighbors(level, graph, pos);
    }

    /** A node's properties changed in place (e.g. a controller formed/unformed). */
    public static void nodeChanged(NetworkNode node) {
        Level level = node.networkLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        DimensionGraph graph = graph(level);
        BlockPos pos = node.networkPos();
        Network cached = graph.byPos.get(pos);
        if (cached != null) {
            cached.markDirty();
        }
        recompute(level, graph, pos.immutable());
    }

    /** A conductor block without a block entity was placed or removed. */
    public static void conductorChanged(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        DimensionGraph graph = graph(level);
        invalidateAround(graph, pos);
        if (isLoaded(level, pos) && isConductor(level, pos)) {
            recompute(level, graph, pos.immutable());
        } else {
            Network old = graph.byPos.remove(pos);
            if (old != null) {
                old.markDirty();
            }
            recomputeNeighbors(level, graph, pos);
        }
    }

    // --- Queries ---

    /** The node's component, recomputed if stale. Null client-side or unloaded. */
    @Nullable
    public static Network networkOf(NetworkNode node) {
        Level level = node.networkLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        return recompute(level, graph(level), node.networkPos());
    }

    /** Nearest registered active controller, for admin tooling. */
    @Nullable
    public static NetworkNode closestActiveController(Level level, BlockPos pos, double maxDistSq) {
        if (level.isClientSide) {
            return null;
        }
        DimensionGraph graph = GRAPHS.get(level.dimension());
        if (graph == null) {
            return null;
        }
        NetworkNode closest = null;
        double closestDistSq = maxDistSq;
        for (NetworkNode node : graph.nodes.values()) {
            if (node.networkLevel() == level && node.isActiveController()) {
                double distSq = node.networkPos().distSqr(pos);
                if (distSq <= closestDistSq) {
                    closest = node;
                    closestDistSq = distSq;
                }
            }
        }
        return closest;
    }

    // --- Internals ---

    private static DimensionGraph graph(Level level) {
        return GRAPHS.computeIfAbsent(level.dimension(), key -> new DimensionGraph());
    }

    private static void invalidateAround(DimensionGraph graph, BlockPos pos) {
        Network at = graph.byPos.get(pos);
        if (at != null) {
            at.markDirty();
        }
        for (Direction direction : DIRECTIONS) {
            Network neighbor = graph.byPos.get(pos.relative(direction));
            if (neighbor != null) {
                neighbor.markDirty();
            }
        }
    }

    private static void recomputeNeighbors(Level level, DimensionGraph graph, BlockPos pos) {
        for (Direction direction : DIRECTIONS) {
            BlockPos next = pos.relative(direction);
            if (isLoaded(level, next) && isConductor(level, next)) {
                recompute(level, graph, next);
            }
        }
    }

    /**
     * Recomputes the component containing {@code seed} unless the cached one
     * is still fresh. Bounded flood fill over loaded conductor blocks;
     * unloaded chunks are component boundaries (machines there are idle
     * anyway, and their nodes re-register on chunk load).
     */
    @Nullable
    private static Network recompute(Level level, DimensionGraph graph, BlockPos seed) {
        Network cached = graph.byPos.get(seed);
        if (cached != null && !cached.isDirty()) {
            return cached;
        }
        if (!isLoaded(level, seed) || !isConductor(level, seed)) {
            graph.byPos.remove(seed);
            return null;
        }
        List<NetworkNode> members = new ArrayList<>();
        List<NetworkNode> controllers = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = seed.immutable();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            NetworkNode node = graph.nodes.get(current);
            if (node != null) {
                members.add(node);
                if (node.isActiveController()) {
                    controllers.add(node);
                }
            }
            if (visited.size() >= MAX_COMPONENT_BLOCKS) {
                LOG.warn("Network component at {} exceeds {} blocks; truncating walk",
                        start, MAX_COMPONENT_BLOCKS);
                break;
            }
            for (Direction direction : DIRECTIONS) {
                BlockPos next = current.relative(direction);
                if (!visited.contains(next) && isLoaded(level, next) && isConductor(level, next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        Network network = new Network(members, controllers);
        for (BlockPos pos : visited) {
            graph.byPos.put(pos, network);
        }
        for (NetworkNode member : members) {
            member.onNetworkChanged(network);
        }
        return network;
    }

    private static boolean isConductor(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof NetworkConductor;
    }

    private static boolean isLoaded(Level level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())) != null;
    }

    private NetworkManager() {
    }
}
