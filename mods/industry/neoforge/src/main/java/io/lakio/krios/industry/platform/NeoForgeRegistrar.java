package io.lakio.krios.industry.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import io.lakio.krios.industry.platform.services.IRegistrar;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NeoForgeRegistrar implements IRegistrar {

    // LinkedHashMap keeps insertion order so registries attach deterministically.
    private static final Map<ResourceKey<? extends Registry<?>>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(Registry<T> registry, ResourceLocation id, Supplier<T> factory) {
        DeferredRegister<T> deferred = (DeferredRegister<T>) REGISTERS.computeIfAbsent(registry.key(),
                key -> DeferredRegister.create(registry.key(), id.getNamespace()));
        return deferred.register(id.getPath(), factory);
    }

    @Override
    public CreativeModeTab.Builder createTabBuilder() {
        return CreativeModeTab.builder();
    }

    /**
     * Must be called from the mod constructor after CommonClass.init() has
     * populated the deferred registers, and before registry events fire.
     */
    public static void attach(IEventBus modBus) {
        REGISTERS.values().forEach(deferred -> deferred.register(modBus));
    }
}
