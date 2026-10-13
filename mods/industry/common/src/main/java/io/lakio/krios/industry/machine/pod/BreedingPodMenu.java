package io.lakio.krios.industry.machine.pod;

import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.zone.CreatureZoneMenu;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Breeding Pod menu: food column, no buffer, the bay, census and cap data. */
public class BreedingPodMenu extends CreatureZoneMenu {

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

    /** Species at the cap (main-frame summary). */
    public int cappedSpecies() {
        return data(BreedingPodBlockEntity.DATA_CAPPED);
    }
}
