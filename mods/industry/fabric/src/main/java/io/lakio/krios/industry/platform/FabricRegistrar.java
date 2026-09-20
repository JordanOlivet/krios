package io.lakio.krios.industry.platform;

import java.util.function.Supplier;

import io.lakio.krios.industry.platform.services.IRegistrar;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class FabricRegistrar implements IRegistrar {

    @Override
    public <T> Supplier<T> register(Registry<T> registry, ResourceLocation id, Supplier<T> factory) {
        T value = Registry.register(registry, id, factory.get());
        return () -> value;
    }

    @Override
    public CreativeModeTab.Builder createTabBuilder() {
        return FabricItemGroup.builder();
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
        return FabricBlockEntityTypeBuilder.create(factory::create, blocks).build();
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> createMenuType(MenuFactory<T> factory) {
        // Constructor widened by Fabric API's transitive access wideners.
        return new MenuType<>(factory::create, FeatureFlags.DEFAULT_FLAGS);
    }
}
