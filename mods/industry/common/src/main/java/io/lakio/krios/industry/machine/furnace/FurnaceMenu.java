package io.lakio.krios.industry.machine.furnace;

import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FurnaceMenu extends AbstractContainerMenu {

    /** 0..3 machine values + 6 face shorts + network status + pos (5) + draw. */
    public static final int DATA_COUNT = 17;

    private final Container container;
    private final ContainerData data;

    private static final int MODULE_SLOT_START = 2;
    private static final int PLAYER_START = 4;
    private static final int PLAYER_END = 40;

    /** Client constructor. */
    public FurnaceMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(4), new SimpleContainerData(DATA_COUNT));
    }

    public FurnaceMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.FURNACE.get(), containerId);
        this.container = container;
        this.data = data;

        addSlot(new Slot(container, FurnaceBlockEntity.INPUT_SLOT, 56, 41));
        addSlot(new Slot(container, FurnaceBlockEntity.OUTPUT_SLOT, 116, 41) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new ModuleSlot(container, FurnaceBlockEntity.MODULE_SLOT_FIRST, 152, 28));
        addSlot(new ModuleSlot(container, FurnaceBlockEntity.MODULE_SLOT_LAST, 152, 52));

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
            if (index < PLAYER_START) {
                if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.getItem() instanceof ModuleItem) {
                if (!moveItemStackTo(stack, MODULE_SLOT_START, PLAYER_START, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, FurnaceBlockEntity.INPUT_SLOT, 1, false)) {
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

    /**
     * The KOIOS gate: a module goes in only when a formed core in range has
     * the required level. Server-authoritative — the client container is a
     * SimpleContainer and predicts optimistically.
     */
    private class ModuleSlot extends Slot {

        ModuleSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof ModuleItem module)) {
                return false;
            }
            if (container instanceof FurnaceBlockEntity be
                    && be.getLevel() != null && !be.getLevel().isClientSide) {
                return be.networkKoiosLevel() >= module.getType().requiredLevel();
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    /** Raw face-config bits for face 0..5 (decode via FaceConfig statics). */
    public int faceBits(int face) {
        return data.get(4 + face);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (container instanceof FurnaceBlockEntity be && !player.level().isClientSide) {
            return be.handleFaceButton(id);
        }
        return false;
    }

    /** 0..width pixels of smelting progress (arrow). */
    public int getProgress(int width) {
        int total = Math.max(1, data.get(1));
        return Math.min(width, data.get(0) * width / total);
    }

    public boolean isWorking() {
        return data.get(0) > 0;
    }

    /** 0..width pixels of stored energy (gauge). */
    public int getEnergyProgress(int width) {
        int progress = (int) ((long) getEnergyStored() * width / FurnaceBlockEntity.CAPACITY);
        return Math.max(0, Math.min(width, progress));
    }

    /** Reassembled from the low/high 16-bit pair (ContainerData syncs shorts). */
    public int getEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public int getEnergyCapacity() {
        return FurnaceBlockEntity.CAPACITY;
    }

    /** KOIOS level of the network controller; 0 while offline. */
    public int networkLevel() {
        return data.get(10);
    }

    /** Machine position, reassembled from the synced low/high pairs. */
    public BlockPos machinePos() {
        int x = (data.get(12) << 16) | (data.get(11) & 0xFFFF);
        int z = (data.get(14) << 16) | (data.get(13) & 0xFFFF);
        return new BlockPos(x, data.get(15), z);
    }

    /** Current energy demand in E/t; 0 while idle. */
    public int drawPerTick() {
        return data.get(16);
    }
}
