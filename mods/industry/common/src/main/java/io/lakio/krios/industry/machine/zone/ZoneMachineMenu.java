package io.lakio.krios.industry.machine.zone;

import io.lakio.krios.industry.machine.MachineMenu;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Shared zone-machine menu (ADR-025): the machine's input column and 3x3
 * output buffer (each zero allowed), module bay, synced zone/state data.
 * The input and module predicates come from the machine — the client menu
 * only sees a SimpleContainer and must not touch the block entity.
 */
public class ZoneMachineMenu extends AbstractContainerMenu implements MachineMenu {

    /**
     * Work-area geometry (option A mockup, 2026-10-08): input column on
     * the left, zone map (or the machine's own display) in the center, 3x3
     * output buffer on the right. Shared with the screens.
     */
    public static final int INPUT_X = 36;
    public static final int SLOT_TOP_Y = 26;
    public static final int BUFFER_X = 180;
    public static final int MAP_X = 93;
    public static final int MAP_Y = 22;
    public static final int MAP_SIZE = 46;
    public static final int CONTROLS_Y = 73;

    private final Container container;
    private final ContainerData data;
    private final Predicate<ItemStack> input;
    private final Predicate<ModuleType> moduleAxes;
    private final int inputSlots;
    private final int moduleSlotStart;
    private final int playerStart;
    // Client-side module bay visibility (annex panel); the server instance
    // stays true so quickMoveStack keeps routing into hidden slots.
    private boolean moduleBayOpen = true;

    /** Client constructor. */
    protected ZoneMachineMenu(MenuType<?> type, int containerId, Inventory playerInventory,
            int inputSlots, int bufferSlots, int dataCount, Predicate<ItemStack> input,
            Predicate<ModuleType> moduleAxes) {
        this(type, containerId, playerInventory,
                new SimpleContainer(ZoneMachineBlockEntity.inventorySize(inputSlots, bufferSlots)),
                new SimpleContainerData(dataCount), inputSlots, bufferSlots, input, moduleAxes);
    }

    protected ZoneMachineMenu(MenuType<?> type, int containerId, Inventory playerInventory,
            Container container, ContainerData data, int inputSlots, int bufferSlots,
            Predicate<ItemStack> input, Predicate<ModuleType> moduleAxes) {
        super(type, containerId);
        this.container = container;
        this.data = data;
        this.input = input;
        this.moduleAxes = moduleAxes;
        this.inputSlots = inputSlots;
        int bufferFirst = inputSlots;
        moduleSlotStart = inputSlots + bufferSlots;
        playerStart = ZoneMachineBlockEntity.inventorySize(inputSlots, bufferSlots);

        for (int i = 0; i < inputSlots; i++) {
            addSlot(new InputSlot(container, i, INPUT_X, SLOT_TOP_Y + i * 18));
        }
        for (int i = 0; i < bufferSlots; i++) {
            addSlot(new OutputSlot(container, bufferFirst + i,
                    BUFFER_X + (i % 3) * 18, SLOT_TOP_Y + (i / 3) * 18));
        }
        for (int i = 0; i < ZoneMachineBlockEntity.MODULE_SLOT_COUNT; i++) {
            addSlot(new ModuleSlot(container, moduleSlotStart + i,
                    MODULE_SLOT_X, MODULE_SLOT_Y + i * MODULE_SLOT_STEP, i));
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
        // Watched terminals refresh live (server side: the block entity).
        container.startOpen(playerInventory.player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    /** Raw synced value (machine data starts at BASE_DATA_COUNT). */
    protected final int data(int index) {
        return data.get(index);
    }

    /** First module-bay slot index (menu and container indexes agree). */
    public int moduleSlotFirst() {
        return moduleSlotStart;
    }

    public int inputSlotCount() {
        return inputSlots;
    }

    @Override
    public void setModuleBayOpen(boolean open) {
        moduleBayOpen = open;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < playerStart) {
                if (!moveItemStackTo(stack, playerStart, playerStart + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.getItem() instanceof ModuleItem) {
                if (!moveItemStackTo(stack, moduleSlotStart, playerStart, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (inputSlots > 0 && input.test(stack)) {
                if (!moveItemStackTo(stack, 0, inputSlots, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
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

    /** Inputs only — what the machine can actually use. */
    private class InputSlot extends Slot {

        InputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return input.test(stack);
        }
    }

    /** Output buffer: take-only. */
    private static class OutputSlot extends Slot {

        OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** Bay slots this machine's tier activates (2 per tier). */
    public int activeModuleSlots() {
        return Math.min(ZoneMachineBlockEntity.MODULE_SLOT_COUNT, 2 * getMachineTier());
    }

    /**
     * The KOIOS gate: a module goes in only when the network's level
     * allows it; the machine tier gates the slot itself, and each machine
     * names the axes it takes (the energy module stays a generator's, Lure
     * and Luck the Quantum Lure's). Server-authoritative.
     */
    private class ModuleSlot extends Slot {

        private final int bayIndex;

        ModuleSlot(Container container, int slot, int x, int y, int bayIndex) {
            super(container, slot, x, y);
            this.bayIndex = bayIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof ModuleItem module)
                    || !moduleAxes.test(module.getType())
                    || bayIndex >= activeModuleSlots()) {
                return false;
            }
            if (container instanceof ZoneMachineBlockEntity be
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
            return moduleBayOpen && bayIndex < activeModuleSlots();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (container instanceof ZoneMachineBlockEntity be && !player.level().isClientSide) {
            return be.handleButton(id);
        }
        return false;
    }

    @Override
    public int faceBits(int face) {
        return data.get(4 + face);
    }

    /** Current zone edge (odd, cells). */
    public int zoneEdge() {
        return Math.max(1, data.get(0));
    }

    /** Largest edge the effective tier allows. */
    public int zoneMaxEdge() {
        return Math.max(1, data.get(1));
    }

    /** ZoneMachineBlockEntity.STATE_* code (or the machine's own) for the voice line. */
    public int stateCode() {
        return data.get(20);
    }

    @Override
    public int getEnergyProgress(int width) {
        int progress = (int) ((long) getEnergyStored() * width / getEnergyCapacity());
        return Math.max(0, Math.min(width, progress));
    }

    public int getMachineTier() {
        return Math.max(1, data.get(18));
    }

    public int getEffectiveTier() {
        return Math.max(1, data.get(19));
    }

    /** Reassembled from the low/high 16-bit pair (ContainerData syncs shorts). */
    @Override
    public int getEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    @Override
    public int getEnergyCapacity() {
        return ZoneMachineBlockEntity.CAPACITY * getMachineTier();
    }

    @Override
    public int networkLevel() {
        return data.get(10);
    }

    @Override
    public BlockPos machinePos() {
        int x = (data.get(12) << 16) | (data.get(11) & 0xFFFF);
        int z = (data.get(14) << 16) | (data.get(13) & 0xFFFF);
        return new BlockPos(x, data.get(15), z);
    }

    /** Average energy draw over the last wake interval, E/t. */
    public int drawPerTick() {
        return data.get(16);
    }

    @Override
    public boolean isStarved() {
        return data.get(17) != 0;
    }
}
