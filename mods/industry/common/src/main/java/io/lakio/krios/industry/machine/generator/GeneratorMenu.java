package io.lakio.krios.industry.machine.generator;

import io.lakio.krios.industry.platform.Services;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GeneratorMenu extends AbstractContainerMenu {

    public static final int FUEL_SLOT = 0;

    private final Container container;
    private final ContainerData data;

    /** Client constructor. */
    public GeneratorMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(1), new SimpleContainerData(4));
    }

    public GeneratorMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.GENERATOR.get(), containerId);
        this.container = container;
        this.data = data;

        addSlot(new Slot(container, FUEL_SLOT, 80, 41) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Services.PLATFORM.getBurnTime(stack) > 0;
            }
        });

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == FUEL_SLOT) {
                if (!moveItemStackTo(stack, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, FUEL_SLOT, 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    public boolean isBurning() {
        return data.get(0) > 0;
    }

    /** 0..height pixels of burn progress remaining (flame indicator). */
    public int getBurnProgress(int height) {
        int duration = Math.max(1, data.get(1));
        return data.get(0) * height / duration;
    }

    /** 0..width pixels of stored energy (gauge). */
    public int getEnergyProgress(int width) {
        int progress = (int) ((long) getEnergyStored() * width / GeneratorBlockEntity.CAPACITY);
        return Math.max(0, Math.min(width, progress));
    }

    /** Reassembled from the low/high 16-bit pair (ContainerData syncs shorts). */
    public int getEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public int getEnergyCapacity() {
        return GeneratorBlockEntity.CAPACITY;
    }
}
