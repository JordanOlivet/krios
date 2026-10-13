package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTabs {

    public static final Supplier<CreativeModeTab> MAIN = Services.REGISTRAR.register(
            BuiltInRegistries.CREATIVE_MODE_TAB, Constants.id("main"),
            () -> Services.REGISTRAR.createTabBuilder()
                    .title(Component.translatable("itemGroup." + Constants.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.CHRONITE_SHARD.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.CHRONITE_ORE.get());
                        output.accept(ModBlocks.DEEPSLATE_CHRONITE_ORE.get());
                        output.accept(ModBlocks.NEURITE_ORE.get());
                        output.accept(ModBlocks.DEEPSLATE_NEURITE_ORE.get());
                        output.accept(ModBlocks.THERMAL_REACTOR.get());
                        output.accept(ModBlocks.THERMAL_REACTOR_TIER2.get());
                        output.accept(ModBlocks.PHOTON_ARRAY.get());
                        output.accept(ModBlocks.FRACTAL_GRINDER.get());
                        output.accept(ModBlocks.FRACTAL_GRINDER_TIER2.get());
                        output.accept(ModBlocks.PLASMA_FORGE.get());
                        output.accept(ModBlocks.PLASMA_FORGE_TIER2.get());
                        output.accept(ModBlocks.CHRONO_FARMER.get());
                        output.accept(ModBlocks.CHRONO_FARMER_TIER2.get());
                        output.accept(ModBlocks.CYCLIC_FORESTER.get());
                        output.accept(ModBlocks.CYCLIC_FORESTER_TIER2.get());
                        output.accept(ModBlocks.QUANTUM_LURE.get());
                        output.accept(ModBlocks.QUANTUM_LURE_TIER2.get());
                        output.accept(ModBlocks.BREEDING_POD.get());
                        output.accept(ModBlocks.BREEDING_POD_TIER2.get());
                        output.accept(ModBlocks.HARVEST_POD.get());
                        output.accept(ModBlocks.HARVEST_POD_TIER2.get());
                        output.accept(ModBlocks.NEURAL_CASING.get());
                        output.accept(ModBlocks.NEURAL_CONDUIT.get());
                        output.accept(ModBlocks.KOIOS_CORE.get());
                        output.accept(ModItems.CHRONITE_SHARD.get());
                        output.accept(ModItems.RAW_NEURITE.get());
                        output.accept(ModItems.NEURITE_DUST.get());
                        output.accept(ModItems.NEURITE_INGOT.get());
                        output.accept(ModItems.ACTUATOR.get());
                        output.accept(ModItems.SPEED_MODULE.get());
                        output.accept(ModItems.EFFICIENCY_MODULE.get());
                        output.accept(ModItems.YIELD_MODULE.get());
                        output.accept(ModItems.SPEED_MODULE_MK2.get());
                        output.accept(ModItems.EFFICIENCY_MODULE_MK2.get());
                        output.accept(ModItems.YIELD_MODULE_MK2.get());
                        output.accept(ModItems.ENERGY_MODULE.get());
                        output.accept(ModItems.LURE_MODULE.get());
                        output.accept(ModItems.LUCK_MODULE.get());
                        output.accept(ModItems.LURE_MODULE_MK2.get());
                        output.accept(ModItems.LUCK_MODULE_MK2.get());
                        output.accept(ModItems.CALIBRATOR.get());
                        output.accept(ModItems.COMPUTE_CHIP.get());
                        output.accept(ModItems.UPGRADE_KIT_TIER2.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }
}
