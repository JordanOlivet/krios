package io.lakio.krios.industry.machine.pod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.ZoneGeometry;
import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Breeding Pod (design 2026-10-09): breeds the animals standing in the
 * zone behind its screen, up to a population cap per species. It breeds
 * directly (user call): two eligible adults of one species, one food item
 * each, then the animal's own vanilla {@code spawnChildFromBreeding} —
 * subclass rules kept, {@code canMate} asked before anything is consumed.
 * Vanilla's breeding XP orb is discarded for now (user call; storable XP
 * waits for krios_storage). No output buffer: it produces animals.
 *
 * <p>Speed modules accelerate the zone's animals' time (baby growth,
 * parent cooldowns) at the usual x(2+pts)/2 pace; Yield modules add bonus
 * babies through the quarter carry.
 */
public class BreedingPodBlockEntity extends ZoneMachineBlockEntity {

    public static final int FOOD_SLOTS = 3;
    /** Zone height from the machine's Y: jumps and slabs. */
    private static final int ZONE_HEIGHT = 3;
    /** Census rows synced to the terminal. */
    public static final int CENSUS_ROWS = 5;

    public static final TagKey<Item> BREEDING_FOOD = TagKey.create(Registries.ITEM, Constants.id("breeding_food"));

    public static final int STATE_CAPPED = 3;
    public static final int STATE_NO_FOOD = 4;
    public static final int STATE_NO_PAIR = 5;
    public static final int STATE_NO_ANIMALS = 6;

    // Cap -/+ (one, or eight with shift).
    public static final int BUTTON_CAP_DOWN = 104;
    public static final int BUTTON_CAP_UP = 105;
    public static final int BUTTON_CAP_DOWN_8 = 106;
    public static final int BUTTON_CAP_UP_8 = 107;

    // Machine data: cap, cap ceiling, species past the census, animals in
    // the zone, then the census rows (entity type raw id + 1, count).
    public static final int DATA_CAP = BASE_DATA_COUNT;
    public static final int DATA_CAP_MAX = BASE_DATA_COUNT + 1;
    public static final int DATA_OTHERS = BASE_DATA_COUNT + 2;
    public static final int DATA_TOTAL = BASE_DATA_COUNT + 3;
    public static final int DATA_CENSUS = BASE_DATA_COUNT + 4;
    public static final int DATA_COUNT = DATA_CENSUS + 2 * CENSUS_ROWS;

    /** Player-set cap; 0 = the tier ceiling (follows upgrades). */
    private int capSetting;

    // Last wake's reading (synced for the terminal).
    private final int[] censusIds = new int[CENSUS_ROWS];
    private final int[] censusCounts = new int[CENSUS_ROWS];
    private int otherSpecies;
    private int totalAnimals;
    private int idle = STATE_NO_ANIMALS;

    public BreedingPodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BREEDING_POD.get(), pos, state, FOOD_SLOTS, 0);
    }

    /** Food the slots take: the tag, minus bucket foods (no buffer for the bucket). */
    public static boolean isFood(ItemStack stack) {
        return stack.is(BREEDING_FOOD) && !(stack.getItem() instanceof BucketItem);
    }

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return isFood(stack);
    }

    @Override
    protected int operationXp() {
        return KoiosBalance.BREED_BASE_XP;
    }

    // --- Cap ---

    private int capMax() {
        return MachineTiers.populationCap(effectiveTier());
    }

    private int cap() {
        int max = capMax();
        return capSetting <= 0 ? max : Math.min(capSetting, max);
    }

    @Override
    protected boolean handleMachineButton(int id) {
        int delta = switch (id) {
            case BUTTON_CAP_DOWN -> -1;
            case BUTTON_CAP_UP -> 1;
            case BUTTON_CAP_DOWN_8 -> -8;
            case BUTTON_CAP_UP_8 -> 8;
            default -> 0;
        };
        if (delta == 0) {
            return false;
        }
        int max = capMax();
        int cap = Math.max(1, Math.min(max, cap() + delta));
        capSetting = cap >= max ? 0 : cap;
        setChanged();
        kick();
        return true;
    }

    // --- Work ---

    private AABB zoneBox() {
        int edge = zoneEdgeForDisplay();
        BlockPos min = ZoneGeometry.min(worldPosition, zoneDirection(), edge);
        BlockPos max = ZoneGeometry.max(worldPosition, zoneDirection(), edge);
        return new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1, min.getY() + ZONE_HEIGHT, max.getZ() + 1);
    }

    @Override
    protected void work(ServerLevel level, Work work) {
        Map<EntityType<?>, List<Animal>> species = new LinkedHashMap<>();
        for (Animal animal : level.getEntitiesOfClass(Animal.class, zoneBox(), Entity::isAlive)) {
            species.computeIfAbsent(animal.getType(), type -> new ArrayList<>()).add(animal);
        }
        if (species.isEmpty()) {
            idle = STATE_NO_ANIMALS;
            census(species);
            return;
        }
        int cap = cap();
        boolean noFood = false;
        int capped = 0;
        for (Map.Entry<EntityType<?>, List<Animal>> entry : species.entrySet()) {
            List<Animal> animals = entry.getValue();
            if (animals.size() >= cap) {
                capped++;
                continue;
            }
            if (breed(level, work, entry.getKey(), animals, cap)) {
                noFood = true;
            }
            if (work.stopped()) {
                break;
            }
        }
        if (!work.starved && work.speedPoints > 0) {
            work.starved = accelerate(level, work, species);
        }
        idle = noFood ? STATE_NO_FOOD : capped == species.size() ? STATE_CAPPED : STATE_NO_PAIR;
        census(species);
    }

    /**
     * Breeds one species' eligible pairs (wake budget, cap); births are
     * appended to {@code animals}. Returns true when a pair was ready but
     * the slots held nothing it eats.
     */
    private boolean breed(ServerLevel level, Work work, EntityType<?> type, List<Animal> animals, int cap) {
        List<Animal> eligible = new ArrayList<>();
        for (Animal animal : animals) {
            if (!animal.isBaby() && animal.getAge() == 0 && animal.canFallInLove()) {
                eligible.add(animal);
            }
        }
        int count = animals.size();
        while (eligible.size() >= 2 && count < cap && work.actions < work.budget) {
            Animal a = eligible.remove(0);
            Animal b = eligible.get(0);
            // canMate needs both in love; it carries the species' own rules
            // (untamed horses and wolves refuse) — nothing consumed on refusal.
            // Silent love (no hearts): the birth itself shows them.
            a.setInLoveTime(600);
            b.setInLoveTime(600);
            if (!a.canMate(b)) {
                a.resetLove();
                b.resetLove();
                continue;
            }
            if (!takeFood(a, b, true)) {
                a.resetLove();
                b.resetLove();
                return true;
            }
            if (!energy.consume(cost(level, KoiosBalance.BREED_ENERGY, work.efficiencyPermille))) {
                a.resetLove();
                b.resetLove();
                work.starved = true;
                return false;
            }
            eligible.remove(0);
            takeFood(a, b, false);
            a.spawnChildFromBreeding(level, b);
            discardBreedingOrbs(level, a);
            count++;
            // Yield: +25% babies per point, quarter carry (ADR-006), no extra food.
            int quarters = yieldQuarters + work.yieldPoints;
            yieldQuarters = quarters % 4;
            for (int bonus = quarters / 4; bonus > 0 && count < cap; bonus--) {
                AgeableMob baby = a.getBreedOffspring(level, b);
                if (baby != null) {
                    baby.setBaby(true);
                    baby.moveTo(a.getX(), a.getY(), a.getZ(), 0.0F, 0.0F);
                    level.addFreshEntityWithPassengers(baby);
                    count++;
                }
            }
            work.report("breed:" + BuiltInRegistries.ENTITY_TYPE.getKey(type));
            work.actions++;
        }
        return false;
    }

    /** One food item per parent, each one it eats; simulate first. */
    private boolean takeFood(Animal a, Animal b, boolean simulate) {
        int slotA = foodSlot(a, -1, simulate);
        if (slotA < 0) {
            return false;
        }
        if (!simulate) {
            items.get(slotA).shrink(1);
        }
        int slotB = foodSlot(b, simulate ? slotA : -1, simulate);
        if (slotB < 0) {
            return false;
        }
        if (!simulate) {
            items.get(slotB).shrink(1);
            setChanged();
        }
        return true;
    }

    /**
     * First slot holding food this animal eats. When simulating the second
     * parent, the first parent's slot counts one item less.
     */
    private int foodSlot(Animal animal, int reservedSlot, boolean simulate) {
        for (int slot = 0; slot < FOOD_SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            int available = stack.getCount() - (simulate && slot == reservedSlot ? 1 : 0);
            if (available > 0 && animal.isFood(stack)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Vanilla's breeding drops an XP orb at the parent (1-7); discarded for
     * now (user call) — only orbs spawned this very tick at the parent.
     */
    private static void discardBreedingOrbs(ServerLevel level, Animal parent) {
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class,
                parent.getBoundingBox().inflate(1.0), orb -> orb.tickCount == 0)) {
            orb.discard();
        }
    }

    /**
     * Speed modules: the zone's animals live faster — babies grow up and
     * parents' cooldowns run out at x(2+pts)/2. Returns true when energy
     * ran out.
     */
    private boolean accelerate(ServerLevel level, Work work, Map<EntityType<?>, List<Animal>> species) {
        int extra = work.elapsed * work.speedPoints / 2;
        if (extra <= 0) {
            return false;
        }
        int animalCost = cost(level, Math.max(1, KoiosBalance.ANIMAL_ACCEL_ENERGY_PER_SECOND * extra / 20),
                work.efficiencyPermille);
        for (List<Animal> animals : species.values()) {
            for (Animal animal : animals) {
                int age = animal.getAge();
                if (age == 0 || !animal.isAlive()) {
                    continue;
                }
                if (!energy.consume(animalCost)) {
                    return true;
                }
                animal.setAge(age < 0 ? Math.min(0, age + extra) : Math.max(0, age - extra));
            }
        }
        return false;
    }

    /** Top species by count for the terminal, the rest summed up. */
    private void census(Map<EntityType<?>, List<Animal>> species) {
        List<Map.Entry<EntityType<?>, List<Animal>>> rows = new ArrayList<>(species.entrySet());
        rows.sort((x, y) -> Integer.compare(y.getValue().size(), x.getValue().size()));
        totalAnimals = 0;
        for (int i = 0; i < CENSUS_ROWS; i++) {
            censusIds[i] = 0;
            censusCounts[i] = 0;
        }
        for (int i = 0; i < rows.size(); i++) {
            int count = rows.get(i).getValue().size();
            totalAnimals += count;
            if (i < CENSUS_ROWS) {
                censusIds[i] = BuiltInRegistries.ENTITY_TYPE.getId(rows.get(i).getKey()) + 1;
                censusCounts[i] = count;
            }
        }
        otherSpecies = Math.max(0, rows.size() - CENSUS_ROWS);
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
        if (index == DATA_CAP_MAX) {
            return capMax();
        }
        if (index == DATA_OTHERS) {
            return otherSpecies;
        }
        if (index == DATA_TOTAL) {
            return totalAnimals;
        }
        int row = (index - DATA_CENSUS) / 2;
        if (row >= 0 && row < CENSUS_ROWS) {
            return (index - DATA_CENSUS) % 2 == 0 ? censusIds[row] : censusCounts[row];
        }
        return 0;
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CapSetting", capSetting);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        capSetting = tag.getInt("CapSetting");
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.breeding_pod");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BreedingPodMenu(containerId, playerInventory, this, dataAccess);
    }
}
