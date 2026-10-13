package io.lakio.krios.industry.machine.field;

import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.zone.CreatureZoneMenu;
import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Phase Death Field menu: no inputs, 3x3 drop buffer, the bay, filter and spare data. */
public class PhaseDeathFieldMenu extends CreatureZoneMenu {

    /** Client constructor. */
    public PhaseDeathFieldMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.PHASE_DEATH_FIELD.get(), containerId, playerInventory, 0,
                ZoneMachineBlockEntity.BUFFER_SLOTS, PhaseDeathFieldBlockEntity.DATA_COUNT,
                stack -> false, ModuleType::isProcessAxis);
    }

    public PhaseDeathFieldMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.PHASE_DEATH_FIELD.get(), containerId, playerInventory, container, data,
                0, ZoneMachineBlockEntity.BUFFER_SLOTS, stack -> false, ModuleType::isProcessAxis);
    }

    /** PhaseDeathFieldBlockEntity.FILTER_* */
    public int filter() {
        return data(PhaseDeathFieldBlockEntity.DATA_FILTER);
    }

    public boolean spareOn() {
        return data(PhaseDeathFieldBlockEntity.DATA_SPARE_ON) != 0;
    }

    public int spareCount() {
        return Math.max(1, data(PhaseDeathFieldBlockEntity.DATA_SPARE_N));
    }
}
