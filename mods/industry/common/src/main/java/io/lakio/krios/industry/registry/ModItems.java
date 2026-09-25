package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class ModItems {

    public static final Supplier<Item> CHRONITE_SHARD = register("chronite_shard",
            () -> new Item(new Item.Properties()));

    public static final Supplier<Item> RAW_NEURITE = register("raw_neurite",
            () -> new Item(new Item.Properties()));

    public static final Supplier<Item> NEURITE_DUST = register("neurite_dust",
            () -> new Item(new Item.Properties()));

    public static final Supplier<Item> NEURITE_INGOT = register("neurite_ingot",
            () -> new Item(new Item.Properties()));

    public static final Supplier<Item> SPEED_MODULE = register("speed_module",
            () -> new ModuleItem(ModuleType.SPEED, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> EFFICIENCY_MODULE = register("efficiency_module",
            () -> new ModuleItem(ModuleType.EFFICIENCY, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> YIELD_MODULE = register("yield_module",
            () -> new ModuleItem(ModuleType.YIELD, new Item.Properties().stacksTo(16)));

    private ModItems() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    private static Supplier<Item> register(String name, Supplier<Item> factory) {
        return Services.REGISTRAR.register(BuiltInRegistries.ITEM, Constants.id(name), factory);
    }
}
