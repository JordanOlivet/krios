package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.platform.Services;
import io.lakio.krios.industry.tool.CalibratorItem;
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
            () -> new ModuleItem(ModuleType.SPEED, 1,
                    KoiosBalance.SPEED_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> EFFICIENCY_MODULE = register("efficiency_module",
            () -> new ModuleItem(ModuleType.EFFICIENCY, 1,
                    KoiosBalance.EFFICIENCY_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> YIELD_MODULE = register("yield_module",
            () -> new ModuleItem(ModuleType.YIELD, 1,
                    KoiosBalance.YIELD_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    // Mk II modules (2 points each) and the reactor's energy module —
    // the N6-N8 ladder of tier 2 (design 2026-10-01).
    public static final Supplier<Item> SPEED_MODULE_MK2 = register("speed_module_mk2",
            () -> new ModuleItem(ModuleType.SPEED, 2,
                    KoiosBalance.SPEED2_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> EFFICIENCY_MODULE_MK2 = register("efficiency_module_mk2",
            () -> new ModuleItem(ModuleType.EFFICIENCY, 2,
                    KoiosBalance.EFFICIENCY2_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> YIELD_MODULE_MK2 = register("yield_module_mk2",
            () -> new ModuleItem(ModuleType.YIELD, 2,
                    KoiosBalance.YIELD2_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> ENERGY_MODULE = register("energy_module",
            () -> new ModuleItem(ModuleType.ENERGY, 1,
                    KoiosBalance.ENERGY_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> CALIBRATOR = register("calibrator",
            () -> new CalibratorItem(new Item.Properties().stacksTo(1)));

    public static final Supplier<Item> COMPUTE_CHIP = register("compute_chip",
            () -> new io.lakio.krios.industry.koios.ComputeChipItem(new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> UPGRADE_KIT_TIER2 = register("upgrade_kit_tier2",
            () -> new io.lakio.krios.industry.machine.UpgradeKitItem(2, new Item.Properties().stacksTo(16)));

    private ModItems() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    private static Supplier<Item> register(String name, Supplier<Item> factory) {
        return Services.REGISTRAR.register(BuiltInRegistries.ITEM, Constants.id(name), factory);
    }
}
