package io.lakio.krios.industry.platform;

import java.util.function.Supplier;

import io.lakio.krios.industry.platform.services.IRegistrar;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

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
}
