package io.lakio.krios.industry.machine.zone;

import io.lakio.krios.industry.machine.module.ModuleType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Planting-machine menu (Chrono Farmer, Cyclic Forester): the plant column
 * plus the pass and voiding data of {@link PlantingZoneBlockEntity}.
 */
public class PlantingZoneMenu extends ZoneMachineMenu {

    /** Client constructor. */
    protected PlantingZoneMenu(MenuType<?> type, int containerId, Inventory playerInventory,
            Predicate<ItemStack> plantable) {
        super(type, containerId, playerInventory, PlantingZoneBlockEntity.PLANT_SLOTS,
                ZoneMachineBlockEntity.BUFFER_SLOTS, PlantingZoneBlockEntity.DATA_COUNT,
                plantable, ModuleType::isProcessAxis);
    }

    protected PlantingZoneMenu(MenuType<?> type, int containerId, Inventory playerInventory,
            Container container, ContainerData data, Predicate<ItemStack> plantable) {
        super(type, containerId, playerInventory, container, data, PlantingZoneBlockEntity.PLANT_SLOTS,
                ZoneMachineBlockEntity.BUFFER_SLOTS, plantable, ModuleType::isProcessAxis);
    }

    /** Workable cells found by the last full pass; -1 until one completes. */
    public int workableCells() {
        return data(ZoneMachineBlockEntity.BASE_DATA_COUNT);
    }

    /** Plantable surplus is destroyed instead of buffered. */
    public boolean voidSurplus() {
        return data(ZoneMachineBlockEntity.BASE_DATA_COUNT + 1) != 0;
    }
}
