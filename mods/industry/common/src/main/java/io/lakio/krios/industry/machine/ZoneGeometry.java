package io.lakio.krios.industry.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Zone shape shared by zone machines and their hologram (Chrono Farmer
 * design 2026-10-08): an odd square starting at the block next to the
 * machine in the zone direction (behind the screen, revised after the
 * first play test), centered on its axis, at the machine's own Y. A
 * shrunk zone stays adjacent to the machine. Server logic and the client
 * renderer derive everything from here, so the two can never disagree.
 */
public final class ZoneGeometry {

    /**
     * Cell {@code index} (0..edge²-1), scanned row by row: rows go away
     * from the machine, each row sweeps across the axis.
     */
    public static BlockPos cell(BlockPos origin, Direction front, int edge, int index) {
        int depth = index / edge + 1;
        int lateral = index % edge - edge / 2;
        return origin.relative(front, depth).relative(front.getClockWise(), lateral);
    }

    /** Minimum corner (lowest x and z) of the zone, at the machine's Y. */
    public static BlockPos min(BlockPos origin, Direction front, int edge) {
        BlockPos a = cell(origin, front, edge, 0);
        BlockPos b = cell(origin, front, edge, edge * edge - 1);
        return new BlockPos(Math.min(a.getX(), b.getX()), origin.getY(), Math.min(a.getZ(), b.getZ()));
    }

    /** Maximum corner (highest x and z) of the zone, at the machine's Y. */
    public static BlockPos max(BlockPos origin, Direction front, int edge) {
        BlockPos a = cell(origin, front, edge, 0);
        BlockPos b = cell(origin, front, edge, edge * edge - 1);
        return new BlockPos(Math.max(a.getX(), b.getX()), origin.getY(), Math.max(a.getZ(), b.getZ()));
    }

    private ZoneGeometry() {
    }
}
