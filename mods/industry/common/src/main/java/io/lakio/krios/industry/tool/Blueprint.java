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
 * player clicked, the structure standing on top of it. Rendering and
 * state live client-side. A future multiblock ships as one more
 * definition in {@link #ALL}; the Calibrator grows a picker once there is
 * something to pick between.
 */
public final class Blueprint {

    /** One required block, relative to the clicked anchor block. */
    public record Cell(BlockPos offset, Supplier<Block> block) {
    }

    /** The 3x3x3 KOIOS core shell: 26 casings around the core, centered. */
    public static final Blueprint KOIOS_CORE = new Blueprint(
            "gui.krios_industry.blueprint.koios_core", Blueprint::buildKoiosCore);

    public static final List<Blueprint> ALL = List.of(KOIOS_CORE);

    private final String nameKey;
    private final Supplier<List<Cell>> factory;
    /** Built on first use — block suppliers must not resolve at class load. */
    private List<Cell> cells;

    private Blueprint(String nameKey, Supplier<List<Cell>> factory) {
        this.nameKey = nameKey;
        this.factory = factory;
    }

    /** Lang key of the structure's display name (actionbar, future picker). */
    public String nameKey() {
        return nameKey;
    }

    public List<Cell> cells() {
        if (cells == null) {
            cells = List.copyOf(factory.get());
        }
        return cells;
    }

    private static List<Cell> buildKoiosCore() {
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
        return cells;
    }
}
