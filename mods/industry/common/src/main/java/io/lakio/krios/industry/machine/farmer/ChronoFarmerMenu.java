package io.lakio.krios.industry.machine.farmer;

import io.lakio.krios.industry.machine.MachineMenu;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
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

public class ChronoFarmerMenu extends AbstractContainerMenu implements MachineMenu {

    /** Zone + energy + faces + status + pos + draw/starved + tiers + cells/state + void toggle. */
    public static final int DATA_COUNT = 23;

    /**
     * Work-area geometry (option A mockup, 2026-10-08): seed column on the
     * left, zone map in the center, 3x3 output buffer on the right. Shared
     * with the screen, which draws the map and its controls around it.
     */
    public static final int SEED_X = 36;
    public static final int SLOT_TOP_Y = 26;
    public static final int BUFFER_X = 180;
    public static final int MAP_X = 93;
    public static final int MAP_Y = 22;
    public static final int MAP_SIZE = 46;
    public static final int CONTROLS_Y = 73;

    private final Container container;
    private final ContainerData data;
    // Client-side module bay visibility (annex panel); the server instance
    // stays true so quickMoveStack keeps routing into hidden slots.
    private boolean moduleBayOpen = true;

    private static final int SEED_START = ChronoFarmerBlockEntity.SEED_FIRST;
    private static final int SEED_END = SEED_START + ChronoFarmerBlockEntity.SEED_SLOTS;
    private static final int MODULE_SLOT_START = ChronoFarmerBlockEntity.MODULE_SLOT_FIRST;
    private static final int PLAYER_START = ChronoFarmerBlockEntity.INVENTORY_SIZE;
    private static final int PLAYER_END = PLAYER_START + 36;

    /** Client constructor. */
    public ChronoFarmerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(ChronoFarmerBlockEntity.INVENTORY_SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    public ChronoFarmerMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.CHRONO_FARMER.get(), containerId);
        this.container = container;
        this.data = data;

        for (int i = 0; i < ChronoFarmerBlockEntity.SEED_SLOTS; i++) {
            addSlot(new SeedSlot(container, ChronoFarmerBlockEntity.SEED_FIRST + i,
                    SEED_X, SLOT_TOP_Y + i * 18));
        }
        for (int i = 0; i < ChronoFarmerBlockEntity.BUFFER_SLOTS; i++) {
            addSlot(new OutputSlot(container, ChronoFarmerBlockEntity.BUFFER_FIRST + i,
                    BUFFER_X + (i % 3) * 18, SLOT_TOP_Y + (i / 3) * 18));
        }
        for (int i = 0; i < ChronoFarmerBlockEntity.MODULE_SLOT_COUNT; i++) {
            addSlot(new ModuleSlot(container, ChronoFarmerBlockEntity.MODULE_SLOT_FIRST + i,
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
            } else if (ChronoFarmerBlockEntity.plantFor(stack) != null) {
                if (!moveItemStackTo(stack, SEED_START, SEED_END, false)) {
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

    /** Seeds only — what the farmer can actually plant. */
    private static class SeedSlot extends Slot {

        SeedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ChronoFarmerBlockEntity.plantFor(stack) != null;
        }
    }

    /** Harvest buffer: take-only. */
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
        return Math.min(ChronoFarmerBlockEntity.MODULE_SLOT_COUNT, 2 * getMachineTier());
    }

    /**
     * The KOIOS gate: a module goes in only when the network's level
     * allows it; the machine tier gates the slot itself, and the energy
     * module stays a generator's. Server-authoritative.
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
                    || module.getType() == ModuleType.ENERGY
                    || bayIndex >= activeModuleSlots()) {
                return false;
            }
            if (container instanceof ChronoFarmerBlockEntity be
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
        if (container instanceof ChronoFarmerBlockEntity be && !player.level().isClientSide) {
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

    /** Workable cells found by the last full pass; -1 until one completes. */
    public int workableCells() {
        return data.get(20);
    }

    /** Plantable surplus is destroyed instead of buffered. */
    public boolean voidSurplusSeeds() {
        return data.get(22) != 0;
    }

    /** ChronoFarmerBlockEntity.STATE_* code for the voice line. */
    public int stateCode() {
        return data.get(21);
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
        return ChronoFarmerBlockEntity.CAPACITY * getMachineTier();
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
