package io.lakio.krios.industry.registry;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.cable.NeuralConduitBlock;
import io.lakio.krios.industry.koios.KoiosCoreBlock;
import io.lakio.krios.industry.koios.NeuralCasingBlock;
import io.lakio.krios.industry.machine.crusher.CrusherBlock;
import io.lakio.krios.industry.machine.furnace.FurnaceBlock;
import io.lakio.krios.industry.machine.generator.GeneratorBlock;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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

    public static final Supplier<Block> DEEPSLATE_CHRONITE_ORE = registerWithItem("deepslate_chronite_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .strength(4.5F, 3.0F)
                    .sound(SoundType.DEEPSLATE)));

    public static final Supplier<Block> DEEPSLATE_NEURITE_ORE = registerWithItem("deepslate_neurite_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .strength(4.5F, 3.0F)
                    .sound(SoundType.DEEPSLATE)));

    public static final Supplier<Block> GENERATOR = registerWithItem("generator",
            () -> new GeneratorBlock(machineProperties(12), 1));

    public static final Supplier<Block> GENERATOR_TIER2 = registerWithItem("generator_tier2",
            () -> new GeneratorBlock(machineProperties(12), 2));

    public static final Supplier<Block> SOLAR_PANEL = registerWithItem("solar_panel",
            // Full cube (machine body + cell deck on top): standard occlusion.
            () -> new io.lakio.krios.industry.machine.solar.SolarPanelBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .requiresCorrectToolForDrops()
                            .strength(3.0F, 4.0F)
                            .sound(SoundType.METAL)));

    public static final Supplier<Block> CRUSHER = registerWithItem("crusher",
            () -> new CrusherBlock(machineProperties(9), 1));

    public static final Supplier<Block> CRUSHER_TIER2 = registerWithItem("crusher_tier2",
            () -> new CrusherBlock(machineProperties(9), 2));

    public static final Supplier<Block> FURNACE = registerWithItem("furnace",
            () -> new FurnaceBlock(machineProperties(11), 1));

    public static final Supplier<Block> FURNACE_TIER2 = registerWithItem("furnace_tier2",
            () -> new FurnaceBlock(machineProperties(11), 2));

    public static final Supplier<Block> NEURAL_CASING = registerWithItem("neural_casing",
            () -> new NeuralCasingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)));

    public static final Supplier<Block> NEURAL_CONDUIT = registerWithItem("neural_conduit",
            () -> new NeuralConduitBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.8F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final Supplier<Block> KOIOS_CORE = registerWithItem("koios_core",
            () -> new KoiosCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL)
                    // Sculpted model (graphics pass): crystal in an open frame.
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(KoiosCoreBlock.FORMED) ? 7 : 0)),
            block -> new BlockItem(block, new Item.Properties()) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context,
                        List<Component> tooltip, TooltipFlag flag) {
                    super.appendHoverText(stack, context, tooltip, flag);
                    tooltip.add(Component.translatable("block.krios_industry.koios_core.hint")
                            .withStyle(ChatFormatting.GRAY));
                }
            });

    /**
     * Shared machine block properties: sculpted models (not full cubes) and
     * a real glow while LIT. Both tiers of a machine share the same shell.
     */
    private static BlockBehaviour.Properties machineProperties(int litLight) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops()
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .lightLevel(state -> state.getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? litLight : 0);
    }

    private ModBlocks() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    private static Supplier<Block> registerWithItem(String name, Supplier<Block> factory) {
        return registerWithItem(name, factory, block -> new BlockItem(block, new Item.Properties()));
    }

    private static Supplier<Block> registerWithItem(String name, Supplier<Block> factory,
            Function<Block, BlockItem> itemFactory) {
        Supplier<Block> block = Services.REGISTRAR.register(BuiltInRegistries.BLOCK, Constants.id(name), factory);
        Services.REGISTRAR.register(BuiltInRegistries.ITEM, Constants.id(name),
                () -> itemFactory.apply(block.get()));
        return block;
    }
}
