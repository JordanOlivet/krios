package io.lakio.krios.industry.machine.crusher;

import io.lakio.krios.industry.machine.MachineMenu;
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

public class CrusherMenu extends AbstractContainerMenu implements MachineMenu {

    /** Machine values + faces + status + pos + draw/starved + tiers + column 2 + distribution. */
    public static final int DATA_COUNT = 23;

    private final Container container;
    private final ContainerData data;
    // Client-side module bay visibility (annex panel); the server instance
    // stays true so quickMoveStack keeps routing into hidden slots.
    private boolean moduleBayOpen = true;

    private static final int MODULE_SLOT_START = CrusherBlockEntity.MODULE_SLOT_FIRST;
    private static final int PLAYER_START = CrusherBlockEntity.INVENTORY_SIZE;
    private static final int PLAYER_END = PLAYER_START + 36;

    /** Client constructor. */
    public CrusherMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CrusherBlockEntity.INVENTORY_SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    public CrusherMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.CRUSHER.get(), containerId);
        this.container = container;
        this.data = data;

        // Process columns (Mekanism-style, vertical): input on top, output
        // below, one pair per column. Columns beyond the machine's tier are
        // inert and hidden via Slot#isActive (the module-bay trick). All
        // inputs first so menu indices mirror the container layout and the
        // shift-click ranges stay contiguous.
        for (int column = 0; column < CrusherBlockEntity.PROCESS_SLOTS; column++) {
            addSlot(new ProcessSlot(container, CrusherBlockEntity.INPUT_FIRST + column,
                    PROCESS_COL_X[column], PROCESS_IN_Y, column, true));
        }
        for (int column = 0; column < CrusherBlockEntity.PROCESS_SLOTS; column++) {
            addSlot(new ProcessSlot(container, CrusherBlockEntity.OUTPUT_FIRST + column,
                    PROCESS_COL_X[column], PROCESS_OUT_Y, column, false));
        }
        for (int i = 0; i < CrusherBlockEntity.MODULE_SLOT_COUNT; i++) {
            addSlot(new ModuleSlot(container, CrusherBlockEntity.MODULE_SLOT_FIRST + i,
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
            if (index < PLAYER_START) {
                if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.getItem() instanceof ModuleItem) {
                if (!moveItemStackTo(stack, MODULE_SLOT_START, PLAYER_START, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveToInputs(stack)) {
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

    /** Columns this machine physically has (menu visibility + routing). */
    public int activeColumns() {
        return Math.min(CrusherBlockEntity.PROCESS_SLOTS,
                io.lakio.krios.industry.machine.MachineTiers.processSlots(getMachineTier()));
    }

    /** Input distribution mode (synced); false = fill first (default). */
    @Override
    public boolean isInputBalance() {
        return data.get(22) != 0;
    }

    /**
     * Shift-click routing honoring the distribution mode: one elected
     * column at a time (item by item in balance mode, so the spread stays
     * even) — hoppers and players behave identically.
     */
    private boolean moveToInputs(ItemStack stack) {
        boolean moved = false;
        while (!stack.isEmpty()) {
            int slot = io.lakio.krios.industry.machine.InputDistribution.chooseColumn(
                    container, CrusherBlockEntity.INPUT_FIRST, activeColumns(), stack, isInputBalance());
            if (slot < 0) {
                break;
            }
            if (!isInputBalance()) {
                if (!moveItemStackTo(stack, slot, slot + 1, false)) {
                    break;
                }
                moved = true;
                continue;
            }
            ItemStack unit = stack.copyWithCount(1);
            if (moveItemStackTo(unit, slot, slot + 1, false) && unit.isEmpty()) {
                stack.shrink(1);
                moved = true;
            } else {
                break;
            }
        }
        return moved;
    }

    /** A process column's input or output; hidden past the machine's tier. */
    private class ProcessSlot extends Slot {

        private final int column;
        private final boolean input;

        ProcessSlot(Container container, int slot, int x, int y, int column, boolean input) {
            super(container, slot, x, y);
            this.column = column;
            this.input = input;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return input && column < activeColumns();
        }

        @Override
        public boolean isActive() {
            return column < activeColumns();
        }
    }

    /** Bay slots this machine's tier activates (2 per tier). */
    public int activeModuleSlots() {
        return Math.min(CrusherBlockEntity.MODULE_SLOT_COUNT, 2 * getMachineTier());
    }

    /**
     * The KOIOS gate: a module goes in only when a formed core in range has
     * the required level; the machine tier gates the slot itself and the
     * machine type gates the axis (no generator energy modules here).
     * Server-authoritative — the client container is a SimpleContainer and
     * predicts optimistically.
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
                    || module.getType() == io.lakio.krios.industry.machine.module.ModuleType.ENERGY
                    || bayIndex >= activeModuleSlots()) {
                return false;
            }
            if (container instanceof CrusherBlockEntity be
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
        if (container instanceof CrusherBlockEntity be && !player.level().isClientSide) {
            return be.handleFaceButton(id);
        }
        return false;
    }

    /** 0..width pixels of a column's crushing progress (vertical bar). */
    public int getColumnProgress(int column, int width) {
        int base = column == 0 ? 0 : 20;
        int total = Math.max(1, data.get(base + 1));
        return Math.min(width, data.get(base) * width / total);
    }

    /** A column is mid-recipe (bar visible / voice line). */
    public boolean isColumnWorking(int column) {
        int base = column == 0 ? 0 : 20;
        return data.get(base) > 0;
    }

    public boolean isWorking() {
        return isColumnWorking(0) || isColumnWorking(1);
    }

    /** 0..width pixels of stored energy (gauge). */
    public int getEnergyProgress(int width) {
        int progress = (int) ((long) getEnergyStored() * width / getEnergyCapacity());
        return Math.max(0, Math.min(width, progress));
    }

    /** Hardware tier of this machine (buffer size, recipes). */
    public int getMachineTier() {
        return Math.max(1, data.get(18));
    }

    /** Effective tier = min(machine, core), live (performance). */
    public int getEffectiveTier() {
        return Math.max(1, data.get(19));
    }

    /** Reassembled from the low/high 16-bit pair (ContainerData syncs shorts). */
    public int getEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public int getEnergyCapacity() {
        return CrusherBlockEntity.CAPACITY * getMachineTier();
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

    /** Working but frozen for lack of energy (voice line). */
    @Override
    public boolean isStarved() {
        return data.get(17) != 0;
    }
}
