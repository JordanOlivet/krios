package io.lakio.krios.industry.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Soil volumes that count as "near water" for vanilla farmland (Chrono
 * Farmer, revised after play 2026-10-08): topping moisture up on each pass
 * left random dry blocks between passes — vanilla only shows the moist
 * texture at moisture 7 and every random tick away from water decrements
 * it. Registered zones make FarmBlock.isNearWater answer true (common
 * mixin), so vanilla keeps the farmland at 7 itself.
 *
 * <p>A v0 static per-level index like KoiosNetwork: machines register in
 * setLevel / on zone resize and unregister in setRemoved. Server thread
 * only; the lookup runs on farmland random ticks (rare), a linear scan over
 * the level's few zones.
 */
public final class HydrationZones {

    private static final Map<LevelReader, Map<BlockPos, BoundingBox>> ZONES = new WeakHashMap<>();

    public static void put(LevelReader level, BlockPos owner, BoundingBox soil) {
        ZONES.computeIfAbsent(level, l -> new HashMap<>()).put(owner.immutable(), soil);
    }

    public static void remove(LevelReader level, BlockPos owner) {
        Map<BlockPos, BoundingBox> zones = ZONES.get(level);
        if (zones != null) {
            zones.remove(owner);
        }
    }

    public static boolean isHydrated(LevelReader level, BlockPos soil) {
        Map<BlockPos, BoundingBox> zones = ZONES.get(level);
        if (zones == null) {
            return false;
        }
        for (BoundingBox box : zones.values()) {
            if (box.isInside(soil)) {
                return true;
            }
        }
        return false;
    }

    private HydrationZones() {
    }
}
