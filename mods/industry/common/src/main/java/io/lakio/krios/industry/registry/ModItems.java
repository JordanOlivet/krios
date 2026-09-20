package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class ModItems {

    public static final Supplier<Item> CHRONITE_SHARD = register("chronite_shard",
            () -> new Item(new Item.Properties()));

    public static final Supplier<Item> RAW_NEURITE = register("raw_neurite",
            () -> new Item(new Item.Properties()));

    private ModItems() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    private static Supplier<Item> register(String name, Supplier<Item> factory) {
        return Services.REGISTRAR.register(BuiltInRegistries.ITEM, Constants.id(name), factory);
    }
}
