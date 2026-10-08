package io.lakio.krios.industry.machine.forester;

import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

public class CyclicForesterMenu extends ZoneMachineMenu {

    /** Client constructor. */
    public CyclicForesterMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.CYCLIC_FORESTER.get(), containerId, playerInventory,
                CyclicForesterBlockEntity::isPlantable);
    }

    public CyclicForesterMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.CYCLIC_FORESTER.get(), containerId, playerInventory, container, data,
                CyclicForesterBlockEntity::isPlantable);
    }
}
