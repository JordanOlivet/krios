package io.lakio.krios.industry.machine.pod;

import java.util.List;
import java.util.Map;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.zone.CreatureZoneBlockEntity;
import io.lakio.krios.industry.mixin.ArmadilloAccessor;
import io.lakio.krios.industry.mixin.MushroomCowAccessor;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Harvest Pod (design 2026-10-13): works the pen behind its screen and
 * collects what living animals give, with no tool (ADR-024).
 *
 * <ul>
 * <li>Pickup: item entities in the zone matching
 * {@code krios_industry:creature_drops} only (eggs, scutes) — never a
 * generic vacuum.</li>
 * <li>Shearing: every vanilla {@link Shearable} animal ready for it, through
 * its own {@code shear()}; the drops it spawns are caught into the buffer.
 * Mooshrooms are never sheared (it turns them into cows). Wool regrowth
 * stays vanilla: the pen needs grass.</li>
 * <li>Milking: cows and goats, one empty bucket in, one milk bucket out;
 * mooshrooms take a bowl first (mushroom stew, or a suspicious stew when
 * fed a flower, as for a player).</li>
 * </ul>
 *
 * No per-job toggles (user): the input is the switch. Speed modules hurry
 * the producers' clocks (egg, scute); Yield adds +25 % per point on wool,
 * eggs and scutes through the quarter carry — never on milk or stew.
 */
public class HarvestPodBlockEntity extends CreatureZoneBlockEntity<Animal> {

    public static final int INPUT_SLOTS = 3;

    public static final TagKey<Item> CREATURE_DROPS = TagKey.create(Registries.ITEM, Constants.id("creature_drops"));

    public static final int STATE_NOTHING_READY = 3;
    public static final int STATE_NO_CONTAINERS = 4;
    public static final int STATE_NO_ANIMALS = 5;

    // Census detail: ready count (low bits) + what the pod does with the
    // species. Short-safe (bit 13 at most).
    public static final int DETAIL_READY_MASK = 0x3FF;
    public static final int FLAG_SHEAR = 1 << 10;
    public static final int FLAG_MILK = 1 << 11;
    public static final int FLAG_STEW = 1 << 12;
    public static final int FLAG_TIMED = 1 << 13;

    private int idle = STATE_NO_ANIMALS;

    public HarvestPodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HARVEST_POD.get(), pos, state, INPUT_SLOTS, BUFFER_SLOTS, Animal.class);
    }

    /** What the input slots take: the containers milking fills. */
    public static boolean isContainer(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.is(Items.BOWL);
    }

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return isContainer(stack);
    }

    @Override
    protected int operationXp() {
        return KoiosBalance.CREATURE_HARVEST_BASE_XP;
    }

    // --- What each animal gives ---

    private static boolean shearable(Animal animal) {
        // Shearing a mooshroom turns it into a cow: never.
        return animal instanceof Shearable && !(animal instanceof MushroomCow);
    }

    /** Cows (mooshrooms included) and goats take a bucket. */
    private static boolean milkable(Animal animal) {
        return animal instanceof Cow || animal instanceof Goat;
    }

    /** Animals laying drops on a clock (eggs, scutes): Speed hurries them. */
    private static boolean timed(Animal animal) {
        return animal instanceof Chicken || animal instanceof Armadillo;
    }

    // --- Work ---

    @Override
    protected void work(ServerLevel level, Work work) {
        Map<EntityType<?>, List<Animal>> species = scan(level);
        pickUp(level, work);
        boolean missingContainer = false;
        for (List<Animal> animals : species.values()) {
            for (Animal animal : animals) {
                if (work.stopped() || work.actions >= work.budget) {
                    break;
                }
                if (shearable(animal)) {
                    shear(level, work, animal);
                } else if (milkable(animal) && !animal.isBaby() && !milk(level, work, animal)) {
                    missingContainer = true;
                }
            }
        }
        if (!work.starved && work.speedPoints > 0) {
            work.starved = accelerate(level, work, species);
        }
        idle = species.isEmpty() ? STATE_NO_ANIMALS
                : missingContainer ? STATE_NO_CONTAINERS
                : STATE_NOTHING_READY;
        census(species);
    }

    /** Yield bonus on {@code count} items; commits the quarter carry when asked. */
    private int bonusItems(int count, int yieldPoints, boolean commit) {
        int quarters = yieldQuarters + yieldPoints * count;
        if (commit) {
            yieldQuarters = quarters % 4;
        }
        return quarters / 4;
    }

    /** Creature drops lying in the zone (tag only), into the buffer. */
    private void pickUp(ServerLevel level, Work work) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, zoneBox(),
                entity -> entity.isAlive() && entity.getItem().is(CREATURE_DROPS))) {
            if (work.actions >= work.budget) {
                return;
            }
            ItemStack stack = item.getItem();
            int count = stack.getCount();
            ItemStack bonus = stack.copyWithCount(bonusItems(count, work.yieldPoints, false));
            List<ItemStack> out = bonus.isEmpty() ? List.of(stack.copy()) : List.of(stack.copy(), bonus);
            if (!stow(out, true)) {
                work.blocked = true;
                return;
            }
            if (!energy.consume(cost(level, KoiosBalance.PICKUP_ENERGY_PER_ITEM * count, work.efficiencyPermille))) {
                work.starved = true;
                return;
            }
            bonusItems(count, work.yieldPoints, true);
            stow(out, false);
            item.discard();
            work.report("pickup:" + BuiltInRegistries.ITEM.getKey(stack.getItem()));
            work.actions++;
            setChanged();
        }
    }

    /**
     * Shears through the animal's own {@code shear()} (modded shearables
     * keep their drops), then catches what it just spawned. Needs one free
     * buffer slot up front; anything that still does not fit stays on the
     * ground.
     */
    private void shear(ServerLevel level, Work work, Animal animal) {
        Shearable shearable = (Shearable) animal;
        if (!shearable.readyForShearing()) {
            return;
        }
        if (!hasFreeBufferSlot()) {
            work.blocked = true;
            return;
        }
        if (!energy.consume(cost(level, KoiosBalance.SHEAR_ENERGY, work.efficiencyPermille))) {
            work.starved = true;
            return;
        }
        shearable.shear(SoundSource.BLOCKS);
        ItemStack[] buffer = bufferView(false);
        int caught = 0;
        Item first = null;
        // Fresh drops only: spawned this very tick around the animal.
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class,
                animal.getBoundingBox().inflate(1.0, 2.0, 1.0),
                entity -> entity.isAlive() && entity.tickCount == 0)) {
            ItemStack stack = drop.getItem();
            if (first == null) {
                first = stack.getItem();
            }
            caught += stack.getCount();
            ItemStack rest = merge(buffer, stack.copy());
            if (rest.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(rest);
            }
        }
        int bonus = bonusItems(caught, work.yieldPoints, true);
        if (first != null && bonus > 0) {
            ItemStack rest = merge(buffer, new ItemStack(first, bonus));
            if (!rest.isEmpty()) {
                animal.spawnAtLocation(rest, 1.0F);
            }
        }
        commitBuffer(buffer);
        work.report("shear:" + BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()));
        work.actions++;
        setChanged();
    }

    /**
     * One milking: a bowl on a mooshroom first, else a bucket. Returns
     * false when the animal could be milked but no container fits it.
     */
    private boolean milk(ServerLevel level, Work work, Animal animal) {
        int bowl = animal instanceof MushroomCow ? inputSlot(Items.BOWL) : -1;
        int bucket = bowl < 0 ? inputSlot(Items.BUCKET) : -1;
        if (bowl < 0 && bucket < 0) {
            return false;
        }
        ItemStack out;
        SoundEvent sound;
        SuspiciousStewEffects effects = null;
        if (bowl >= 0) {
            effects = ((MushroomCowAccessor) animal).krios$getStewEffects();
            if (effects != null) {
                out = new ItemStack(Items.SUSPICIOUS_STEW);
                out.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, effects);
                sound = SoundEvents.MOOSHROOM_MILK_SUSPICIOUSLY;
            } else {
                out = new ItemStack(Items.MUSHROOM_STEW);
                sound = SoundEvents.MOOSHROOM_MILK;
            }
        } else {
            out = new ItemStack(Items.MILK_BUCKET);
            sound = animal instanceof Goat goat
                    ? goat.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_MILK : SoundEvents.GOAT_MILK
                    : SoundEvents.COW_MILK;
        }
        if (!stow(List.of(out.copy()), true)) {
            work.blocked = true;
            return true;
        }
        if (!energy.consume(cost(level, KoiosBalance.MILK_ENERGY, work.efficiencyPermille))) {
            work.starved = true;
            return true;
        }
        if (effects != null) {
            ((MushroomCowAccessor) animal).krios$setStewEffects(null);
        }
        items.get(bowl >= 0 ? bowl : bucket).shrink(1);
        stow(List.of(out), false);
        level.playSound(null, animal, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        work.report("milk:" + BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()));
        work.actions++;
        setChanged();
        return true;
    }

    private int inputSlot(Item item) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (items.get(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    // --- Speed: egg and scute clocks ---

    @Override
    protected boolean accelerable(Animal animal) {
        if (animal.isBaby()) {
            return false;
        }
        if (animal instanceof Chicken chicken) {
            return !chicken.isChickenJockey() && chicken.eggTime > 1;
        }
        return animal instanceof Armadillo && ((ArmadilloAccessor) animal).krios$getScuteTime() > 1;
    }

    @Override
    protected void accelerateCreature(Animal animal, int ticks) {
        // Down to 1, never past: vanilla's own tick lays the drop.
        if (animal instanceof Chicken chicken) {
            chicken.eggTime = Math.max(1, chicken.eggTime - ticks);
        } else if (animal instanceof ArmadilloAccessor armadillo) {
            armadillo.krios$setScuteTime(Math.max(1, armadillo.krios$getScuteTime() - ticks));
        }
    }

    // --- Census ---

    @Override
    protected int censusDetail(EntityType<?> type, List<Animal> animals) {
        Animal sample = animals.get(0);
        int flags = 0;
        int ready = 0;
        if (shearable(sample)) {
            flags |= FLAG_SHEAR;
            for (Animal animal : animals) {
                if (((Shearable) animal).readyForShearing()) {
                    ready++;
                }
            }
        } else if (milkable(sample) || timed(sample)) {
            flags |= milkable(sample) ? FLAG_MILK : FLAG_TIMED;
            if (sample instanceof MushroomCow) {
                flags |= FLAG_STEW;
            }
            for (Animal animal : animals) {
                if (!animal.isBaby()) {
                    ready++;
                }
            }
        }
        return flags | Math.min(ready, DETAIL_READY_MASK);
    }

    @Override
    protected int idleState() {
        return idle;
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.harvest_pod");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new HarvestPodMenu(containerId, playerInventory, this, dataAccess);
    }
}
