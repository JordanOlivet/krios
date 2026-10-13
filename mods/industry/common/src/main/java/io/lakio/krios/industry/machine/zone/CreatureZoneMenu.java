package io.lakio.krios.industry.machine.zone;

import java.util.function.Predicate;

import io.lakio.krios.industry.machine.module.ModuleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Creature-machine menu (ADR-025 amendment): the census accessors. */
public class CreatureZoneMenu extends ZoneMachineMenu {

    /** Client constructor. */
    protected CreatureZoneMenu(MenuType<?> type, int containerId, Inventory playerInventory,
            int inputSlots, int bufferSlots, int dataCount, Predicate<ItemStack> input,
            Predicate<ModuleType> moduleAxes) {
        super(type, containerId, playerInventory, inputSlots, bufferSlots, dataCount, input, moduleAxes);
    }

    protected CreatureZoneMenu(MenuType<?> type, int containerId, Inventory playerInventory,
            Container container, ContainerData data, int inputSlots, int bufferSlots,
            Predicate<ItemStack> input, Predicate<ModuleType> moduleAxes) {
        super(type, containerId, playerInventory, container, data, inputSlots, bufferSlots, input, moduleAxes);
    }

    /** Animals in the zone, all species. */
    public int totalAnimals() {
        return data(CreatureZoneBlockEntity.DATA_TOTAL);
    }

    /** Species in the zone, census rows included. */
    public int speciesTotal() {
        return data(CreatureZoneBlockEntity.DATA_SPECIES);
    }

    /** Census row's species, or null for an empty row. */
    @Nullable
    public EntityType<?> species(int row) {
        int id = data(CreatureZoneBlockEntity.DATA_CENSUS + 3 * row) - 1;
        return id < 0 ? null : BuiltInRegistries.ENTITY_TYPE.byId(id);
    }

    public int speciesCount(int row) {
        return data(CreatureZoneBlockEntity.DATA_CENSUS + 3 * row + 1);
    }

    /** The machine's per-row detail (see its block entity). */
    public int censusDetail(int row) {
        return data(CreatureZoneBlockEntity.DATA_CENSUS + 3 * row + 2);
    }
}
