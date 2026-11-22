package io.lakio.krios.core.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import io.lakio.krios.core.platform.services.IRegistrar;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NeoForgeRegistrar implements IRegistrar {

    /** One deferred register per registry and namespace: each mod attaches its own to its own bus. */
    private record Key(ResourceKey<? extends Registry<?>> registry, String namespace) {
    }

    // LinkedHashMap keeps insertion order so registries attach deterministically.
    private static final Map<Key, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(Registry<T> registry, ResourceLocation id, Supplier<T> factory) {
        DeferredRegister<T> deferred = (DeferredRegister<T>) REGISTERS.computeIfAbsent(
                new Key(registry.key(), id.getNamespace()),
                key -> DeferredRegister.create(registry.key(), id.getNamespace()));
        return deferred.register(id.getPath(), factory);
    }

    @Override
    public CreativeModeTab.Builder createTabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
        // BlockEntitySupplier is made public by NeoForge's access transformers.
        return BlockEntityType.Builder.of(factory::create, blocks).build(null);
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> createMenuType(MenuFactory<T> factory) {
        // Constructor made public by NeoForge's access transformers.
        return new MenuType<>(factory::create, FeatureFlags.DEFAULT_FLAGS);
    }

    /**
     * Hands the deferred registers of one namespace to that mod's bus. Must
     * be called from the mod constructor after its CommonClass.init() has
     * populated them, and before registry events fire.
     */
    public static void attach(IEventBus modBus, String namespace) {
        REGISTERS.forEach((key, deferred) -> {
            if (key.namespace().equals(namespace)) {
                deferred.register(modBus);
            }
        });
    }
}
