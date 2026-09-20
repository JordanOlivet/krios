package io.lakio.krios.industry.platform.services;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Platform-neutral registration. Fabric registers immediately; NeoForge defers
 * to registry events. Common code must never assume the returned supplier is
 * resolvable before registries are frozen.
 */
public interface IRegistrar {

    <T> Supplier<T> register(Registry<T> registry, ResourceLocation id, Supplier<T> factory);

    /**
     * Creative tab builders differ per loader (Fabric assigns row/index itself,
     * NeoForge patches a no-arg builder), so construction goes through the platform.
     */
    CreativeModeTab.Builder createTabBuilder();

    /**
     * Vanilla keeps {@code BlockEntityType.BlockEntitySupplier} package-private
     * and each loader widens it differently, so construction goes through the platform.
     */
    <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... blocks);

    /** Vanilla keeps the {@link MenuType} constructor private; same treatment. */
    <T extends AbstractContainerMenu> MenuType<T> createMenuType(MenuFactory<T> factory);

    @FunctionalInterface
    interface BlockEntityFactory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }

    @FunctionalInterface
    interface MenuFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory playerInventory);
    }
}
