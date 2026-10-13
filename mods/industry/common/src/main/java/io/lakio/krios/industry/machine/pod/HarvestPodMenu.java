package io.lakio.krios.industry.machine.pod;

import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.zone.CreatureZoneBlockEntity;
import io.lakio.krios.industry.machine.zone.CreatureZoneMenu;
import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;

/** Harvest Pod menu: container column, 3x3 buffer, the bay, census data. */
public class HarvestPodMenu extends CreatureZoneMenu {

    /** Client constructor. */
    public HarvestPodMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.HARVEST_POD.get(), containerId, playerInventory, HarvestPodBlockEntity.INPUT_SLOTS,
                ZoneMachineBlockEntity.BUFFER_SLOTS, CreatureZoneBlockEntity.CREATURE_DATA_COUNT,
                HarvestPodBlockEntity::isContainer, ModuleType::isProcessAxis);
    }

    public HarvestPodMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.HARVEST_POD.get(), containerId, playerInventory, container, data,
                HarvestPodBlockEntity.INPUT_SLOTS, ZoneMachineBlockEntity.BUFFER_SLOTS,
                HarvestPodBlockEntity::isContainer, ModuleType::isProcessAxis);
    }

    /** Whether the input column holds this container (census marks). */
    public boolean hasInput(Item item) {
        for (int slot = 0; slot < HarvestPodBlockEntity.INPUT_SLOTS; slot++) {
            if (getSlot(slot).getItem().is(item)) {
                return true;
            }
        }
        return false;
    }
}
