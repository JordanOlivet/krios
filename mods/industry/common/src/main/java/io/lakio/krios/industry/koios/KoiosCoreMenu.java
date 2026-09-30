package io.lakio.krios.industry.koios;

import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Display-only menu: no item slots, ten synced values. */
public class KoiosCoreMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 10;

    private final ContainerData data;
    @Nullable
    private final BlockEntity blockEntity;

    /** Client constructor. */
    public KoiosCoreMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, new SimpleContainerData(DATA_COUNT));
    }

    public KoiosCoreMenu(int containerId, Inventory playerInventory, @Nullable BlockEntity blockEntity, ContainerData data) {
        super(ModMenus.KOIOS_CORE.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        addDataSlots(data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity == null || Container.stillValidBlockEntity(blockEntity, player);
    }

    public int getKoiosLevel() {
        return Math.max(1, data.get(0));
    }

    public boolean isFormed() {
        return data.get(1) != 0;
    }

    /** Distinct/total operations in the window, per-mille. */
    public int getDiversityPermille() {
        return data.get(2);
    }

    /** Distinct operation kinds currently in the window (tooltip). */
    public int getDistinctOps() {
        return data.get(5);
    }

    /** Operations currently held by the window (tooltip). */
    public int getWindowFill() {
        return data.get(6);
    }

    /** Network energy fill per-mille (1000 = healthy/no buffers). */
    public int getEnergyPermille() {
        return data.get(7);
    }

    /** Demand outpaces production on the network. */
    public boolean isEnergyDeficit() {
        return data.get(8) != 0;
    }

    /** Reassembled from the low/high 16-bit pair (ContainerData syncs shorts). */
    public int getXp() {
        return (data.get(4) << 16) | (data.get(3) & 0xFFFF);
    }

    /** Hardware tier of the core (1..MAX_TIER). */
    public int getTier() {
        return Math.max(1, data.get(9));
    }

    /** True once XP sits at this tier's critical threshold. */
    public boolean isCriticalPending() {
        return getTier() < KoiosBalance.MAX_TIER && getXp() >= KoiosBalance.xpCap(getTier());
    }

    /** The paid breakthrough exists in game (next tier's curve shipped). */
    public boolean isBreakthroughAvailable() {
        return KoiosBalance.hasTierCurve(getTier() + 1);
    }

    public int getLevelFloorXp() {
        return KoiosBalance.LEVEL_XP[Math.min(KoiosBalance.criticalLevel(getTier()), getKoiosLevel())];
    }

    public int getNextLevelXp() {
        return KoiosBalance.LEVEL_XP[Math.min(KoiosBalance.criticalLevel(getTier()), getKoiosLevel() + 1)];
    }

    /** 0..width pixels of progress toward the next level (XP bar). */
    public int getXpBarWidth(int width) {
        int floor = getLevelFloorXp();
        int next = getNextLevelXp();
        if (next <= floor) {
            return width;
        }
        int current = Math.max(0, getXp() - floor);
        return Math.min(width, current * width / (next - floor));
    }
}
