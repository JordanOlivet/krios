package io.lakio.krios.industry.machine.pod;

import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import org.jetbrains.annotations.Nullable;

/** Breeding Pod menu: food column, no buffer, the bay, cap and census data. */
public class BreedingPodMenu extends ZoneMachineMenu {

    /** Client constructor. */
    public BreedingPodMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.BREEDING_POD.get(), containerId, playerInventory, BreedingPodBlockEntity.FOOD_SLOTS, 0,
                BreedingPodBlockEntity.DATA_COUNT, BreedingPodBlockEntity::isFood, ModuleType::isProcessAxis);
    }

    public BreedingPodMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.BREEDING_POD.get(), containerId, playerInventory, container, data,
                BreedingPodBlockEntity.FOOD_SLOTS, 0, BreedingPodBlockEntity::isFood, ModuleType::isProcessAxis);
    }

    /** Population cap per species in force. */
    public int cap() {
        return Math.max(1, data(BreedingPodBlockEntity.DATA_CAP));
    }

    /** Cap ceiling of the effective tier. */
    public int capMax() {
        return Math.max(1, data(BreedingPodBlockEntity.DATA_CAP_MAX));
    }

    /** Species present beyond the census rows. */
    public int otherSpecies() {
        return data(BreedingPodBlockEntity.DATA_OTHERS);
    }

    /** Animals in the zone, all species. */
    public int totalAnimals() {
        return data(BreedingPodBlockEntity.DATA_TOTAL);
    }

    /** Census row's species, or null for an empty row. */
    @Nullable
    public EntityType<?> species(int row) {
        int id = data(BreedingPodBlockEntity.DATA_CENSUS + 2 * row) - 1;
        return id < 0 ? null : BuiltInRegistries.ENTITY_TYPE.byId(id);
    }

    public int speciesCount(int row) {
        return data(BreedingPodBlockEntity.DATA_CENSUS + 2 * row + 1);
    }
}
