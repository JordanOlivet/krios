package io.lakio.krios.industry.koios;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * v0 core index: a per-dimension list of loaded cores so machines can report
 * operations to the closest formed core in range. Server thread only. Cores
 * register in {@code setLevel} and unregister in {@code setRemoved} (vanilla
 * calls the latter on chunk unload too); stale entries are purged lazily as a
 * safety net. Replaced by the real network with scheduled wake-ups later.
 */
public final class KoiosNetwork {

    private static final Map<ResourceKey<Level>, List<KoiosCoreBlockEntity>> CORES = new HashMap<>();

    public static void add(KoiosCoreBlockEntity core) {
        List<KoiosCoreBlockEntity> list = CORES.computeIfAbsent(
                core.getLevel().dimension(), key -> new ArrayList<>());
        if (!list.contains(core)) {
            list.add(core);
        }
    }

    public static void remove(KoiosCoreBlockEntity core) {
        List<KoiosCoreBlockEntity> list = CORES.get(core.getLevel().dimension());
        if (list != null) {
            list.remove(core);
        }
    }

    /** KOIOS level available at a position, or 0 when no formed core is in range. */
    public static int coreLevelAt(Level level, BlockPos pos) {
        KoiosCoreBlockEntity core = closestFormed(level, pos);
        return core == null ? 0 : core.getKoiosLevel();
    }

    public static void reportOperations(Level level, BlockPos pos, String key, int count, int baseXp) {
        KoiosCoreBlockEntity core = closestFormed(level, pos);
        if (core != null) {
            core.reportOperations(key, count, baseXp);
        }
    }

    @Nullable
    public static KoiosCoreBlockEntity closestFormed(Level level, BlockPos pos) {
        List<KoiosCoreBlockEntity> list = CORES.get(level.dimension());
        if (list == null || list.isEmpty()) {
            return null;
        }
        KoiosCoreBlockEntity closest = null;
        double closestDistSq = KoiosBalance.CORE_RANGE_SQ;
        Iterator<KoiosCoreBlockEntity> it = list.iterator();
        while (it.hasNext()) {
            KoiosCoreBlockEntity core = it.next();
            if (core.isRemoved() || core.getLevel() != level) {
                it.remove();
                continue;
            }
            if (!core.isFormed()) {
                continue;
            }
            double distSq = core.getBlockPos().distSqr(pos);
            if (distSq <= closestDistSq) {
                closest = core;
                closestDistSq = distSq;
            }
        }
        return closest;
    }

    private KoiosNetwork() {
    }
}
