package io.lakio.krios.industry.platform.services;

import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

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
}
