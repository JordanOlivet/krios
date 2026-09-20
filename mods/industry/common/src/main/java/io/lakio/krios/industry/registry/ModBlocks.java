package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.generator.GeneratorBlock;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {

    public static final Supplier<Block> CHRONITE_ORE = registerWithItem("chronite_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 3.0F)
                    .sound(SoundType.STONE)));

    public static final Supplier<Block> NEURITE_ORE = registerWithItem("neurite_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 3.0F)
                    .sound(SoundType.STONE)));

    public static final Supplier<Block> GENERATOR = registerWithItem("generator",
            () -> new GeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)));

    private ModBlocks() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    private static Supplier<Block> registerWithItem(String name, Supplier<Block> factory) {
        Supplier<Block> block = Services.REGISTRAR.register(BuiltInRegistries.BLOCK, Constants.id(name), factory);
        Services.REGISTRAR.register(BuiltInRegistries.ITEM, Constants.id(name),
                () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
