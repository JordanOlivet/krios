package io.lakio.krios.industry.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.lakio.krios.industry.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * Holographic construction blueprints (art direction: the construction
 * guide IS the hologram). Pure data — offsets from the anchor block the
 * player clicked, the structure standing on top of it. Rendering and state
 * live client-side; future multiblocks add their own definitions here.
 */
public final class Blueprint {

    /** One required block, relative to the clicked anchor block. */
    public record Cell(BlockPos offset, Supplier<Block> block) {
    }

    private static List<Cell> koiosCore;

    /** The 3x3x3 KOIOS core shell: 26 casings around the core, centered. */
    public static List<Cell> koiosCore() {
        if (koiosCore == null) {
            List<Cell> cells = new ArrayList<>(27);
            for (int dy = 1; dy <= 3; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean center = dx == 0 && dz == 0 && dy == 2;
                        cells.add(new Cell(new BlockPos(dx, dy, dz),
                                center ? ModBlocks.KOIOS_CORE : ModBlocks.NEURAL_CASING));
                    }
                }
            }
            koiosCore = List.copyOf(cells);
        }
        return koiosCore;
    }

    private Blueprint() {
    }
}
