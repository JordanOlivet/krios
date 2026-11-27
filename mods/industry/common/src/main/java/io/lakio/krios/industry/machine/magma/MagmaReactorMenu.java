package io.lakio.krios.industry.machine.magma;

import io.lakio.krios.industry.machine.MachineMenu;
import io.lakio.krios.industry.machine.MachineTiers;
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
import net.minecraft.world.item.Items;

public class MagmaReactorMenu extends AbstractContainerMenu implements MachineMenu {

    public static final int BUCKET_IN_SLOT = MagmaReactorBlockEntity.BUCKET_IN_SLOT;
    public static final int BUCKET_OUT_SLOT = MagmaReactorBlockEntity.BUCKET_OUT_SLOT;
    private static final int MODULE_SLOT_START = MagmaReactorBlockEntity.MODULE_SLOT_FIRST;
    private static final int PLAYER_START = MagmaReactorBlockEntity.INVENTORY_SIZE;
    private static final int PLAYER_END = PLAYER_START + 36;
    /** 0..3 machine values + 6 face shorts + status + pos (5) + tiers (2) + lava (2). */
    public static final int DATA_COUNT = 20;

    /** Bucket slots, either side of the flame in the work area. */
    public static final int BUCKET_IN_X = 100;
    public static final int BUCKET_OUT_X = 138;
    public static final int BUCKET_Y = 60;

    private final Container container;
    private final ContainerData data;
    // Client-side module bay visibility (annex panel); the server instance
    // stays true so quickMoveStack keeps routing into hidden slots.
    private boolean moduleBayOpen = true;

    /** Client constructor. */
    public MagmaReactorMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(MagmaReactorBlockEntity.INVENTORY_SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    public MagmaReactorMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.MAGMA_REACTOR.get(), containerId);
        this.container = container;
        this.data = data;

        addSlot(new Slot(container, BUCKET_IN_SLOT, BUCKET_IN_X, BUCKET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAVA_BUCKET);
            }
        });
        addSlot(new Slot(container, BUCKET_OUT_SLOT, BUCKET_OUT_X, BUCKET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int i = 0; i < MagmaReactorBlockEntity.MODULE_SLOT_COUNT; i++) {
            addSlot(new ModuleSlot(container, MagmaReactorBlockEntity.MODULE_SLOT_FIRST + i,
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
            } else if (stack.getItem() instanceof ModuleItem) {
                if (!moveItemStackTo(stack, MODULE_SLOT_START, PLAYER_START, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!stack.is(Items.LAVA_BUCKET)
                    || !moveItemStackTo(stack, BUCKET_IN_SLOT, BUCKET_IN_SLOT + 1, false)) {
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

    /** The bucket input, while it takes this item (recipe viewers fill through it). */
    @Override
    public int workInputSlot(ItemStack stack) {
        Slot in = getSlot(BUCKET_IN_SLOT);
        return in.getItem().isEmpty() && in.mayPlace(stack) ? BUCKET_IN_SLOT : -1;
    }

    @Override
    public void setModuleBayOpen(boolean open) {
        moduleBayOpen = open;
    }

    /** Two slots per hardware tier: four at tier 2. */
    public int activeModuleSlots() {
        return Math.min(MagmaReactorBlockEntity.MODULE_SLOT_COUNT, 2 * getMachineTier());
    }

    /** Energy points in the bay, uncapped (the stats line shows what counts). */
    public int bayEnergyPoints() {
        return ModuleItem.pointsIn(container, MODULE_SLOT_START, MagmaReactorBlockEntity.MODULE_SLOT_COUNT,
                ModuleType.ENERGY, -1);
    }

    /**
     * Energy modules only, one point per effective tier at most (ADR-039),
     * KOIOS-gated like every module insertion. Server-authoritative; the
     * client predicts with the synced effective tier.
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
                    || module.getType() != ModuleType.ENERGY
                    || bayIndex >= activeModuleSlots()) {
                return false;
            }
            if (!MachineTiers.energyModuleFits(
                    ModuleItem.pointsIn(container, MODULE_SLOT_START, MagmaReactorBlockEntity.MODULE_SLOT_COUNT,
                            ModuleType.ENERGY, getContainerSlot()),
                    module.points(), getEffectiveTier())) {
                return false;
            }
            if (container instanceof MagmaReactorBlockEntity be
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
    public int faceBits(int face) {
        return data.get(4 + face);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (container instanceof MagmaReactorBlockEntity be && !player.level().isClientSide) {
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
        return Math.min(height, data.get(0) * height / duration);
    }

    /** 0..width pixels of stored energy (gauge). */
    public int getEnergyProgress(int width) {
        int progress = (int) ((long) getEnergyStored() * width / getEnergyCapacity());
        return Math.max(0, Math.min(width, progress));
    }

    /** 0..width pixels of lava in the tank (gauge). */
    public int getLavaProgress(int width) {
        int progress = (int) ((long) getLavaStored() * width / getLavaCapacity());
        return Math.max(0, Math.min(width, progress));
    }

    @Override
    public int getMachineTier() {
        return Math.max(1, data.get(16));
    }

    /** Effective tier = min(machine, core), live (performance). */
    public int getEffectiveTier() {
        return Math.max(1, data.get(17));
    }

    public int getEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public int getEnergyCapacity() {
        return MagmaReactorBlockEntity.CAPACITY_PER_TIER * getMachineTier();
    }

    public int getLavaStored() {
        return (data.get(19) << 16) | (data.get(18) & 0xFFFF);
    }

    public int getLavaCapacity() {
        return MagmaReactorBlockEntity.TANK_PER_TIER * getMachineTier();
    }

    /** KOIOS level of the network controller; 0 while offline. */
    public int networkLevel() {
        return data.get(10);
    }

    public BlockPos machinePos() {
        int x = (data.get(12) << 16) | (data.get(11) & 0xFFFF);
        int z = (data.get(14) << 16) | (data.get(13) & 0xFFFF);
        return new BlockPos(x, data.get(15), z);
    }
}
