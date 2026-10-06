package io.lakio.krios.industry.machine.photon;

import io.lakio.krios.industry.machine.MachineMenu;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * No machine slots at all (Mk I — the Mk II boost bay will add some):
 * the terminal is a pure readout plus the face hologram. Same synced
 * layout as the reactor menu, burn data replaced by production data.
 */
public class PhotonArrayMenu extends AbstractContainerMenu implements MachineMenu {

    /** 0 rate, 1 state, 2..3 energy pair + 6 face shorts + status + pos (5). */
    public static final int DATA_COUNT = 16;

    @Nullable
    private final PhotonArrayBlockEntity panel;
    private final ContainerData data;

    /** Client constructor. */
    public PhotonArrayMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, new SimpleContainerData(DATA_COUNT));
    }

    public PhotonArrayMenu(int containerId, Inventory playerInventory,
            @Nullable PhotonArrayBlockEntity panel, ContainerData data) {
        super(ModMenus.PHOTON_ARRAY.get(), containerId);
        this.panel = panel;
        this.data = data;

        // 256-wide codex terminal layout (MachineTerminalScreen).
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

    /** Only player slots exist: shift-click hops between inventory and hotbar. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 27) {
                if (!moveItemStackTo(stack, 27, 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 27, false)) {
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
        return panel == null || Container.stillValidBlockEntity(panel, player);
    }

    /** Raw face-config bits for face 0..5 (decode via FaceConfig statics). */
    @Override
    public int faceBits(int face) {
        return data.get(4 + face);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (panel != null && !player.level().isClientSide) {
            return panel.handleFaceButton(id);
        }
        return false;
    }

    /** Live output in E/t; 0 while dark or saturated. */
    public int getRate() {
        return data.get(0);
    }

    /** One of the PhotonArrayBlockEntity.STATE_* codes. */
    public int getProductionState() {
        return data.get(1);
    }

    /** 0..width pixels of stored energy (gauge). */
    @Override
    public int getEnergyProgress(int width) {
        int progress = (int) ((long) getEnergyStored() * width / getEnergyCapacity());
        return Math.max(0, Math.min(width, progress));
    }

    /** Reassembled from the low/high 16-bit pair (ContainerData syncs shorts). */
    @Override
    public int getEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    @Override
    public int getEnergyCapacity() {
        return PhotonArrayBlockEntity.CAPACITY;
    }

    /** KOIOS level of the network controller; 0 while offline. */
    @Override
    public int networkLevel() {
        return data.get(10);
    }

    /** Machine position, reassembled from the synced low/high pairs. */
    @Override
    public BlockPos machinePos() {
        int x = (data.get(12) << 16) | (data.get(11) & 0xFFFF);
        int z = (data.get(14) << 16) | (data.get(13) & 0xFFFF);
        return new BlockPos(x, data.get(15), z);
    }
}
