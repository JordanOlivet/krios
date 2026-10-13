package io.lakio.krios.industry.machine.synth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.ZoneGeometry;
import io.lakio.krios.industry.machine.zone.CreatureZoneBlockEntity;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Bio Synthesizer (design 2026-10-13): synthesizes mobs in the zone
 * behind its screen. Without an Engram it draws from the MONSTER spawn
 * list of the biome at the machine; with one, only the imprinted species,
 * whatever the biome. Light is ignored, vanilla placement is kept (spawn
 * placement type, free space inside the zone). The Engram is data: never
 * consumed.
 *
 * <p>One cycle every 200 ticks (Speed shortens it); the effective tier
 * doubles the mobs per cycle (1/2/4/8) and caps the mobs in the zone
 * (8/16/32/64); Yield adds +25 % mobs per point through the quarter carry.
 * Energy is paid per maximum health point: the matter is synthesized.
 */
public class BioSynthesizerBlockEntity extends CreatureZoneBlockEntity<Mob> {

    public static final int ENGRAM_SLOTS = 1;

    public static final int STATE_CAPPED = 3;
    public static final int STATE_NO_ROOM = 4;
    public static final int STATE_NOTHING_SPAWNS = 5;
    public static final int STATE_PEACEFUL = 6;

    /** Random positions tried per mob before giving up for this cycle. */
    private static final int PLACEMENT_TRIES = 12;

    public static final int DATA_CAP = CREATURE_DATA_COUNT;
    public static final int DATA_COUNT = CREATURE_DATA_COUNT + 1;

    private int idle = STATE_NOTHING_SPAWNS;

    public BioSynthesizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIO_SYNTHESIZER.get(), pos, state, ENGRAM_SLOTS, 0, Mob.class);
    }

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return EngramItem.isImprinted(stack);
    }

    @Override
    protected int operationXp() {
        return KoiosBalance.SPAWN_BASE_XP;
    }

    private int cap() {
        return MachineTiers.populationCap(effectiveTier());
    }

    // --- Work ---

    @Override
    protected void work(ServerLevel level, Work work) {
        Map<EntityType<?>, List<Mob>> species = scan(level);
        int present = 0;
        for (List<Mob> mobs : species.values()) {
            present += mobs.size();
        }
        int cap = MachineTiers.populationCap(work.effectiveTier);
        if (present >= cap) {
            idle = STATE_CAPPED;
            census(species);
            return;
        }
        EntityType<?> engram = EngramItem.species(items.get(0));
        if (engram == null && level.getBiome(worldPosition).value().getMobSettings()
                .getMobs(MobCategory.MONSTER).isEmpty()) {
            idle = STATE_NOTHING_SPAWNS;
            census(species);
            return;
        }
        int wanted = MachineTiers.speedFactor(work.effectiveTier);
        int quarters = yieldQuarters + work.yieldPoints * wanted;
        yieldQuarters = quarters % 4;
        wanted = Math.min(wanted + quarters / 4, cap - present);
        boolean peaceful = false;
        boolean noRoom = false;
        for (int i = 0; i < wanted && !work.stopped(); i++) {
            EntityType<?> type = engram != null ? engram : drawFromBiome(level);
            if (type == null) {
                continue;
            }
            if (level.getDifficulty() == Difficulty.PEACEFUL && !type.getCategory().isFriendly()) {
                peaceful = true;
                break;
            }
            BlockPos pos = findPlace(level, type);
            if (pos == null) {
                noRoom = true;
                continue;
            }
            Mob born = spawn(level, work, type, pos);
            if (born != null) {
                // Counted at once: the census must not lag a cycle behind.
                species.computeIfAbsent(type, key -> new ArrayList<>()).add(born);
            }
        }
        idle = peaceful ? STATE_PEACEFUL : noRoom ? STATE_NO_ROOM : STATE_NOTHING_SPAWNS;
        census(species);
    }

    @Nullable
    private EntityType<?> drawFromBiome(ServerLevel level) {
        Optional<MobSpawnSettings.SpawnerData> data = level.getBiome(worldPosition).value().getMobSettings()
                .getMobs(MobCategory.MONSTER).getRandom(level.random);
        return data.map(spawner -> spawner.type).orElse(null);
    }

    /**
     * A random cell of the zone, then the lowest Y where the species may
     * stand (its vanilla placement type, light ignored) with its whole box
     * free and inside the zone's height.
     */
    @Nullable
    private BlockPos findPlace(ServerLevel level, EntityType<?> type) {
        int edge = zoneEdgeForDisplay();
        if (edge <= 0) {
            return null;
        }
        BlockPos min = ZoneGeometry.min(worldPosition, zoneDirection(), edge);
        int top = min.getY() + zoneHeight();
        for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
            int x = min.getX() + level.random.nextInt(edge);
            int z = min.getZ() + level.random.nextInt(edge);
            for (int y = min.getY(); y + Mth.ceil(type.getHeight()) <= top; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos)) {
                    break;
                }
                if (SpawnPlacements.isSpawnPositionOk(type, level, pos)
                        && level.noCollision(type.getSpawnAABB(x + 0.5, y, z + 0.5))) {
                    return pos;
                }
            }
        }
        return null;
    }

    /** The new mob, or null when it could not be made (or paid for). */
    @Nullable
    private Mob spawn(ServerLevel level, Work work, EntityType<?> type, BlockPos pos) {
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        int cost = cost(level, KoiosBalance.SYNTH_ENERGY_PER_HEALTH * Mth.ceil(mob.getMaxHealth()),
                work.efficiencyPermille);
        if (!energy.consume(cost)) {
            mob.discard();
            work.starved = true;
            return null;
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null);
        level.addFreshEntityWithPassengers(mob);
        // The vanilla spawner's puff, at the new mob.
        level.levelEvent(2004, pos, 0);
        mob.spawnAnim();
        work.report("spawn:" + BuiltInRegistries.ENTITY_TYPE.getKey(type));
        work.actions++;
        setChanged();
        return mob;
    }

    /** One cycle every 200 ticks, shortened by Speed; the idle backoff otherwise. */
    @Override
    protected int nextDelay(Work work) {
        if (work.actions <= 0) {
            return KoiosBalance.ZONE_IDLE_DELAY_TICKS;
        }
        return Math.max(1, KoiosBalance.SYNTH_CYCLE_TICKS * 2 / (2 + work.speedPoints));
    }

    @Override
    protected int idleState() {
        return idle;
    }

    // --- Synced data ---

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int machineData(int index) {
        if (index == DATA_CAP) {
            return cap();
        }
        return super.machineData(index);
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.bio_synthesizer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BioSynthesizerMenu(containerId, playerInventory, this, dataAccess);
    }
}
