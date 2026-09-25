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
                        output.accept(ModBlocks.GENERATOR.get());
                        output.accept(ModBlocks.CRUSHER.get());
                        output.accept(ModBlocks.NEURAL_CASING.get());
                        output.accept(ModBlocks.NEURAL_CONDUIT.get());
                        output.accept(ModBlocks.KOIOS_CORE.get());
                        output.accept(ModItems.CHRONITE_SHARD.get());
                        output.accept(ModItems.RAW_NEURITE.get());
                        output.accept(ModItems.NEURITE_DUST.get());
                        output.accept(ModItems.NEURITE_INGOT.get());
                        output.accept(ModItems.SPEED_MODULE.get());
                        output.accept(ModItems.EFFICIENCY_MODULE.get());
                        output.accept(ModItems.YIELD_MODULE.get());
                        output.accept(ModItems.CALIBRATOR.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }
}
