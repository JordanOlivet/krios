package io.lakio.krios.industry.registry;

import io.lakio.krios.core.platform.Services;
import io.lakio.krios.industry.IndustryBalance;
import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class ModItems {

    /**
     * First intermediate (roadmap rule: born with its first consuming
     * machine, the Chrono Farmer); reused by the Forester, the Mining Rig
     * and later lines.
     */
    public static final Supplier<Item> ACTUATOR = register("actuator",
            () -> new Item(new Item.Properties()));

    public static final Supplier<Item> SPEED_MODULE = register("speed_module",
            () -> new ModuleItem(ModuleType.SPEED, 1,
                    IndustryBalance.SPEED_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> EFFICIENCY_MODULE = register("efficiency_module",
            () -> new ModuleItem(ModuleType.EFFICIENCY, 1,
                    IndustryBalance.EFFICIENCY_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> YIELD_MODULE = register("yield_module",
            () -> new ModuleItem(ModuleType.YIELD, 1,
                    IndustryBalance.YIELD_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    // Mk II modules (2 points each) and the reactor's energy module —
    // the N6-N8 ladder of tier 2 (design 2026-10-01).
    public static final Supplier<Item> SPEED_MODULE_MK2 = register("speed_module_mk2",
            () -> new ModuleItem(ModuleType.SPEED, 2,
                    IndustryBalance.SPEED2_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> EFFICIENCY_MODULE_MK2 = register("efficiency_module_mk2",
            () -> new ModuleItem(ModuleType.EFFICIENCY, 2,
                    IndustryBalance.EFFICIENCY2_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> YIELD_MODULE_MK2 = register("yield_module_mk2",
            () -> new ModuleItem(ModuleType.YIELD, 2,
                    IndustryBalance.YIELD2_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> ENERGY_MODULE = register("energy_module",
            () -> new ModuleItem(ModuleType.ENERGY, 1,
                    IndustryBalance.ENERGY_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    // Quantum Lure only (ADR-024): a fishing rod's enchantments as modules.
    public static final Supplier<Item> LURE_MODULE = register("lure_module",
            () -> new ModuleItem(ModuleType.LURE, 1,
                    IndustryBalance.LURE_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> LUCK_MODULE = register("luck_module",
            () -> new ModuleItem(ModuleType.LUCK, 1,
                    IndustryBalance.LUCK_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> LURE_MODULE_MK2 = register("lure_module_mk2",
            () -> new ModuleItem(ModuleType.LURE, 2,
                    IndustryBalance.LURE2_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> LUCK_MODULE_MK2 = register("luck_module_mk2",
            () -> new ModuleItem(ModuleType.LUCK, 2,
                    IndustryBalance.LUCK2_UNLOCK_LEVEL, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> UPGRADE_KIT_TIER2 = register("upgrade_kit_tier2",
            () -> new io.lakio.krios.industry.machine.UpgradeKitItem(2, new Item.Properties().stacksTo(16)));

    public static final Supplier<Item> ENGRAM = register("engram",
            () -> new io.lakio.krios.industry.machine.synth.EngramItem(new Item.Properties().stacksTo(16)));

    private ModItems() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    private static Supplier<Item> register(String name, Supplier<Item> factory) {
        return Services.REGISTRAR.register(BuiltInRegistries.ITEM, Constants.id(name), factory);
    }
}
