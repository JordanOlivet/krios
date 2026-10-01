package io.lakio.krios.industry.machine.generator;

import io.lakio.krios.industry.machine.MachineMenu;
import io.lakio.krios.industry.platform.Services;
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

public class GeneratorMenu extends AbstractContainerMenu implements MachineMenu {

    public static final int FUEL_SLOT = 0;
    private static final int MODULE_SLOT_START = GeneratorBlockEntity.MODULE_SLOT_FIRST;
    private static final int PLAYER_START = GeneratorBlockEntity.INVENTORY_SIZE;
    private static final int PLAYER_END = PLAYER_START + 36;
    /** 0..3 machine values + 6 face shorts + status + pos (5) + tiers (2). */
    public static final int DATA_COUNT = 18;

    private final Container container;
    private final ContainerData data;
    // Client-side module bay visibility (annex panel); the server instance
    // stays true so quickMoveStack keeps routing into hidden slots.
    private boolean moduleBayOpen = true;

    /** Client constructor. */
    public GeneratorMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(GeneratorBlockEntity.INVENTORY_SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    public GeneratorMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.GENERATOR.get(), containerId);
        this.container = container;
        this.data = data;

        // 256-wide codex terminal layout (MachineTerminalScreen); the
        // bar/flame/slot column sits centered between header and voice.
        addSlot(new Slot(container, FUEL_SLOT, 120, 60) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Services.PLATFORM.getBurnTime(stack) > 0;
            }
        });
        for (int i = 0; i < GeneratorBlockEntity.MODULE_SLOT_COUNT; i++) {
            addSlot(new ModuleSlot(container, GeneratorBlockEntity.MODULE_SLOT_FIRST + i,
                    MachineMenu.MODULE_SLOT_X, MachineMenu.MODULE_SLOT_Y + i * MachineMenu.MODULE_SLOT_STEP, i));
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 47 + col * 18, 116 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(playerInventory, col, 47 + col * 18, 174));
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
            } else if (stack.getItem() instanceof io.lakio.krios.industry.machine.module.ModuleItem) {
                if (!moveItemStackTo(stack, MODULE_SLOT_START, PLAYER_START, false)) {
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
    public void setModuleBayOpen(boolean open) {
        moduleBayOpen = open;
    }

    /** The bay only exists at tier 2+ (t1 stays deliberately boring). */
    public int activeModuleSlots() {
        return getMachineTier() >= 2 ? GeneratorBlockEntity.MODULE_SLOT_COUNT : 0;
    }

    /**
     * The generator's bay accepts ONLY energy modules (design: the one
     * way to extract more from the same fuel), KOIOS-gated like every
     * module insertion. Server-authoritative.
     */
    private class ModuleSlot extends Slot {

        private final int bayIndex;

        ModuleSlot(Container container, int slot, int x, int y, int bayIndex) {
            super(container, slot, x, y);
            this.bayIndex = bayIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof io.lakio.krios.industry.machine.module.ModuleItem module)
                    || module.getType() != io.lakio.krios.industry.machine.module.ModuleType.ENERGY
                    || bayIndex >= activeModuleSlots()) {
                return false;
            }
            if (container instanceof GeneratorBlockEntity be
                    && be.getLevel() != null && !be.getLevel().isClientSide) {
                return be.networkKoiosLevel() >= module.requiredLevel();
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            // Collapsed bay or inert tier slot: hidden, client-side only.
            return moduleBayOpen && bayIndex < activeModuleSlots();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    /** Raw face-config bits for face 0..5 (decode via FaceConfig statics). */
    @Override
    public int faceBits(int face) {
        return data.get(4 + face);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (container instanceof GeneratorBlockEntity be && !player.level().isClientSide) {
            return be.handleFaceButton(id);
        }
        return false;
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
        int progress = (int) ((long) getEnergyStored() * width / getEnergyCapacity());
        return Math.max(0, Math.min(width, progress));
    }

    /** Hardware tier of this machine (buffer size, recipes). */
    public int getMachineTier() {
        return Math.max(1, data.get(16));
    }

    /** Effective tier = min(machine, core), live (performance). */
    public int getEffectiveTier() {
        return Math.max(1, data.get(17));
    }

    /** Reassembled from the low/high 16-bit pair (ContainerData syncs shorts). */
    public int getEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public int getEnergyCapacity() {
        return GeneratorBlockEntity.CAPACITY * getMachineTier();
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
}
