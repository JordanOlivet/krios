package io.lakio.krios.industry.machine.farmer;

import io.lakio.krios.industry.machine.zone.PlantingZoneMenu;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

public class ChronoFarmerMenu extends PlantingZoneMenu {

    /** Client constructor. */
    public ChronoFarmerMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.CHRONO_FARMER.get(), containerId, playerInventory,
                ChronoFarmerBlockEntity::isPlantable);
    }

    public ChronoFarmerMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.CHRONO_FARMER.get(), containerId, playerInventory, container, data,
                ChronoFarmerBlockEntity::isPlantable);
    }
}
