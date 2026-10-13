package io.lakio.krios.industry.machine.zone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.ZoneGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Creature layer of the zone machines (ADR-025, amended with the Harvest
 * Pod): what every machine working on creatures shares — the three-block
 * zone box, the scan grouped by species, the census synced to the
 * terminal's Census annex, and Speed as time acceleration on them. The
 * scan class is per machine (ADR-026): animals for the pods, every mob
 * for the Phase Death Field.
 *
 * @param <E> the creatures this machine works on
 */
public abstract class CreatureZoneBlockEntity<E extends Mob> extends ZoneMachineBlockEntity {

    /** Zone height from the machine's Y: jumps and slabs. */
    private static final int ZONE_HEIGHT = 3;
    /** Census rows synced to the terminal (the annex's capacity). */
    public static final int CENSUS_ROWS = 8;

    // Creature data: animals in the zone, species count, then the census
    // rows (entity type raw id + 1, count, machine detail). Machine data
    // starts at CREATURE_DATA_COUNT.
    public static final int DATA_TOTAL = BASE_DATA_COUNT;
    public static final int DATA_SPECIES = BASE_DATA_COUNT + 1;
    public static final int DATA_CENSUS = BASE_DATA_COUNT + 2;
    public static final int CREATURE_DATA_COUNT = DATA_CENSUS + 3 * CENSUS_ROWS;

    // Last wake's reading (synced for the terminal).
    private final int[] censusIds = new int[CENSUS_ROWS];
    private final int[] censusCounts = new int[CENSUS_ROWS];
    private final int[] censusDetails = new int[CENSUS_ROWS];
    private int speciesCount;
    private int totalAnimals;
    private final Class<E> scanClass;

    protected CreatureZoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            int inputSlots, int bufferSlots, Class<E> scanClass) {
        super(type, pos, state, inputSlots, bufferSlots);
        this.scanClass = scanClass;
    }

    // --- Subclass contract ---

    /** Whether Speed modules have anything to hurry on this creature. */
    protected boolean accelerable(E creature) {
        return false;
    }

    /** Moves this creature's clocks forward by {@code ticks}. */
    protected void accelerateCreature(E creature, int ticks) {
    }

    /** Per-row census detail for the terminal (short-safe); 0 by default. */
    protected int censusDetail(EntityType<?> type, List<E> creatures) {
        return 0;
    }

    // --- Zone ---

    protected final AABB zoneBox() {
        int edge = zoneEdgeForDisplay();
        BlockPos min = ZoneGeometry.min(worldPosition, zoneDirection(), edge);
        BlockPos max = ZoneGeometry.max(worldPosition, zoneDirection(), edge);
        return new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1, min.getY() + ZONE_HEIGHT, max.getZ() + 1);
    }

    /** Living creatures in the zone, grouped by species (encounter order). */
    protected final Map<EntityType<?>, List<E>> scan(ServerLevel level) {
        Map<EntityType<?>, List<E>> species = new LinkedHashMap<>();
        for (E creature : level.getEntitiesOfClass(scanClass, zoneBox(), Entity::isAlive)) {
            species.computeIfAbsent(creature.getType(), type -> new ArrayList<>()).add(creature);
        }
        return species;
    }

    // --- Speed: animal time ---

    /**
     * Speed modules: the zone's creatures live faster, at x(2+pts)/2 —
     * each machine names the clocks it hurries. Returns true when energy
     * ran out.
     */
    protected final boolean accelerate(ServerLevel level, Work work, Map<EntityType<?>, List<E>> species) {
        int extra = work.elapsed * work.speedPoints / 2;
        if (extra <= 0) {
            return false;
        }
        int animalCost = cost(level, Math.max(1, KoiosBalance.ANIMAL_ACCEL_ENERGY_PER_SECOND * extra / 20),
                work.efficiencyPermille);
        for (List<E> creatures : species.values()) {
            for (E creature : creatures) {
                if (!creature.isAlive() || !accelerable(creature)) {
                    continue;
                }
                if (!energy.consume(animalCost)) {
                    return true;
                }
                accelerateCreature(creature, extra);
            }
        }
        return false;
    }

    // --- Census ---

    /** Top species by count for the terminal; the rest only counted. */
    protected final void census(Map<EntityType<?>, List<E>> species) {
        List<Map.Entry<EntityType<?>, List<E>>> rows = new ArrayList<>(species.entrySet());
        rows.sort((x, y) -> Integer.compare(y.getValue().size(), x.getValue().size()));
        totalAnimals = 0;
        for (int i = 0; i < CENSUS_ROWS; i++) {
            censusIds[i] = 0;
            censusCounts[i] = 0;
            censusDetails[i] = 0;
        }
        for (int i = 0; i < rows.size(); i++) {
            Map.Entry<EntityType<?>, List<E>> row = rows.get(i);
            int count = row.getValue().size();
            totalAnimals += count;
            if (i < CENSUS_ROWS) {
                censusIds[i] = BuiltInRegistries.ENTITY_TYPE.getId(row.getKey()) + 1;
                censusCounts[i] = count;
                censusDetails[i] = censusDetail(row.getKey(), row.getValue());
            }
        }
        speciesCount = rows.size();
    }

    // --- Synced data ---

    @Override
    protected int dataCount() {
        return CREATURE_DATA_COUNT;
    }

    @Override
    protected int machineData(int index) {
        if (index == DATA_TOTAL) {
            return totalAnimals;
        }
        if (index == DATA_SPECIES) {
            return speciesCount;
        }
        int offset = index - DATA_CENSUS;
        int row = offset / 3;
        if (offset >= 0 && row < CENSUS_ROWS) {
            return switch (offset % 3) {
                case 0 -> censusIds[row];
                case 1 -> censusCounts[row];
                default -> censusDetails[row];
            };
        }
        return 0;
    }
}
