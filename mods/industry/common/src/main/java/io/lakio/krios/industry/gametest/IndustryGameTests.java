package io.lakio.krios.industry.gametest;

import java.util.List;

import com.mojang.datafixers.util.Either;

import io.lakio.krios.core.gametest.CoreGameTests;
import io.lakio.krios.core.koios.KoiosCoreBlockEntity;
import io.lakio.krios.core.network.Network;
import io.lakio.krios.core.network.NetworkManager;
import io.lakio.krios.core.registry.ModBlocks;
import io.lakio.krios.core.registry.ModItems;
import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.grinder.FractalGrinderBlockEntity;
import io.lakio.krios.industry.machine.grinder.GrindingRecipe;
import io.lakio.krios.industry.machine.reactor.ThermalReactorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Industry's in-world tests (ADR-035), one rule each, in core's empty arena.
 * Same registration and runs as {@link CoreGameTests}.
 */
public class IndustryGameTests {

    private static final String BATCH = "krios_industry";

    @GameTestGenerator
    public List<TestFunction> tests() {
        return List.of(
                new TestFunction(BATCH, "reactor_keeps_the_bucket", CoreGameTests.ARENA, 100, 0L, true,
                        IndustryGameTests::reactorKeepsTheBucket),
                new TestFunction(BATCH, "grinder_off_network_runs_at_furnace_pace", CoreGameTests.ARENA, 400, 0L, true,
                        IndustryGameTests::grinderOffNetworkRunsAtFurnacePace),
                new TestFunction(BATCH, "machine_tier_capped_by_the_core", CoreGameTests.ARENA, 200, 0L, true,
                        IndustryGameTests::machineTierCappedByTheCore),
                new TestFunction(BATCH, "grinder_doubles_raw_iron_on_a_network", CoreGameTests.ARENA, 600, 0L, true,
                        IndustryGameTests::grinderDoublesRawIronOnANetwork),
                new TestFunction(BATCH, "tag_result_gives_the_preferred_item", CoreGameTests.ARENA, 20, 0L, true,
                        IndustryGameTests::tagResultGivesThePreferredItem),
                new TestFunction(BATCH, "vanilla_material_recipes_are_available", CoreGameTests.ARENA, 20, 0L, true,
                        IndustryGameTests::vanillaMaterialRecipesAreAvailable),
                new TestFunction(BATCH, "absent_material_recipes_are_unavailable", CoreGameTests.ARENA, 20, 0L, true,
                        IndustryGameTests::absentMaterialRecipesAreUnavailable));
    }

    /** The metals other mods bring, each with an ore and a raw recipe (ADR-037). */
    private static final List<String> MODDED_METALS = List.of("aluminum", "antimony", "chromium", "cobalt",
            "iridium", "lead", "nickel", "osmium", "platinum", "silver", "tin", "titanium", "tungsten", "uranium",
            "zinc");

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static GrindingRecipe grinding(GameTestHelper helper, String name) {
        ResourceLocation id = Constants.id("grinding_" + name);
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
        helper.assertTrue(holder != null && holder.value() instanceof GrindingRecipe,
                id + " is missing or did not load as a grinding recipe");
        return (GrindingRecipe) holder.value();
    }

    /**
     * On a network a tier 1 grinder gives the recipe's own count: one raw
     * iron becomes two Krios iron dusts.
     */
    private static void grinderDoublesRawIronOnANetwork(GameTestHelper helper) {
        CoreGameTests.buildShell(helper);
        BlockPos grinderPos = new BlockPos(5, 1, 2);
        BlockPos reactorPos = new BlockPos(6, 1, 2);
        helper.setBlock(new BlockPos(4, 1, 2), ModBlocks.NEURAL_CONDUIT.get());
        helper.setBlock(grinderPos, io.lakio.krios.industry.registry.ModBlocks.FRACTAL_GRINDER.get());
        helper.setBlock(reactorPos, io.lakio.krios.industry.registry.ModBlocks.THERMAL_REACTOR.get());
        FractalGrinderBlockEntity grinder = helper.getBlockEntity(grinderPos);
        ThermalReactorBlockEntity reactor = helper.getBlockEntity(reactorPos);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Network network = NetworkManager.networkOf(grinder);
                    helper.assertTrue(network != null && network.isValid(), "the grinder is on no valid network");
                })
                .thenExecute(() -> {
                    reactor.setItem(ThermalReactorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL_BLOCK));
                    grinder.setItem(FractalGrinderBlockEntity.INPUT_FIRST, new ItemStack(Items.RAW_IRON));
                })
                .thenWaitUntil(() -> {
                    ItemStack out = grinder.getItem(FractalGrinderBlockEntity.OUTPUT_FIRST);
                    helper.assertTrue(out.is(ModItems.IRON_DUST.get()), "the output slot holds " + out);
                    helper.assertValueEqual(out.getCount(), 2, "iron dusts from one raw iron");
                })
                .thenSucceed();
    }

    /**
     * A result written as a tag gives one item, the same on every run: the
     * Krios dust where Krios has one, the vanilla item otherwise.
     */
    private static void tagResultGivesThePreferredItem(GameTestHelper helper) {
        GrindingRecipe dust = new GrindingRecipe(Ingredient.of(Items.RAW_IRON),
                Either.right(new GrindingRecipe.TagResult(itemTag("dusts/iron"), 2)), 600, 120);
        helper.assertTrue(dust.available(), "a recipe with a populated result tag is unavailable");
        helper.assertTrue(dust.result().is(ModItems.IRON_DUST.get()) && dust.result().getCount() == 2,
                "c:dusts/iron x2 gave " + dust.result());
        GrindingRecipe raw = new GrindingRecipe(Ingredient.of(Items.IRON_ORE),
                Either.right(new GrindingRecipe.TagResult(itemTag("raw_materials/iron"), 2)), 800, 160);
        helper.assertTrue(raw.result().is(Items.RAW_IRON), "c:raw_materials/iron gave " + raw.result());
        helper.succeed();
    }

    /** Every vanilla ore and raw metal has its recipe, loaded and able to run. */
    private static void vanillaMaterialRecipesAreAvailable(GameTestHelper helper) {
        for (String name : List.of("iron_ore", "gold_ore", "copper_ore", "raw_iron", "raw_gold", "raw_copper",
                "coal_ore", "diamond_ore", "emerald_ore", "quartz_ore", "redstone_ore", "lapis_ore")) {
            helper.assertTrue(grinding(helper, name).available(), "grinding_" + name + " is unavailable");
        }
        GrindingRecipe rawGold = grinding(helper, "raw_gold");
        helper.assertTrue(rawGold.matches(new SingleRecipeInput(new ItemStack(Items.RAW_GOLD)), helper.getLevel()),
                "raw gold does not match its recipe");
        helper.assertTrue(rawGold.result().is(ModItems.GOLD_DUST.get()), "raw gold gives " + rawGold.result());
        helper.succeed();
    }

    /**
     * A recipe for a metal no installed mod brings is loaded but cannot run:
     * no result, nothing matches it (not even the barrier NeoForge shows for
     * an empty tag), and the recipe viewers skip it.
     */
    private static void absentMaterialRecipesAreUnavailable(GameTestHelper helper) {
        for (String metal : MODDED_METALS) {
            for (String name : List.of(metal + "_ore", "raw_" + metal)) {
                GrindingRecipe recipe = grinding(helper, name);
                helper.assertTrue(!recipe.available(), "grinding_" + name + " is available without its mod");
                helper.assertTrue(recipe.result().isEmpty(), "grinding_" + name + " gives " + recipe.result());
                helper.assertTrue(
                        !recipe.matches(new SingleRecipeInput(new ItemStack(Items.BARRIER)), helper.getLevel()),
                        "grinding_" + name + " matches a barrier");
            }
        }
        helper.succeed();
    }

    /** A lava bucket burns and leaves its bucket in the fuel slot, as in a furnace. */
    private static void reactorKeepsTheBucket(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, io.lakio.krios.industry.registry.ModBlocks.THERMAL_REACTOR.get());
        ThermalReactorBlockEntity reactor = helper.getBlockEntity(pos);
        reactor.setItem(ThermalReactorBlockEntity.FUEL_SLOT, new ItemStack(Items.LAVA_BUCKET));
        helper.succeedWhen(() -> {
            ItemStack left = reactor.getItem(ThermalReactorBlockEntity.FUEL_SLOT);
            helper.assertTrue(left.is(Items.BUCKET), "the fuel slot holds " + left);
            helper.assertTrue(reactor.energyStored() > 0, "the lava gave no energy");
        });
    }

    /**
     * Off a network a grinder works like a furnace: 200 ticks an operation,
     * not the recipe's 60. Its energy comes from the reactor beside it.
     */
    private static void grinderOffNetworkRunsAtFurnacePace(GameTestHelper helper) {
        BlockPos reactorPos = new BlockPos(1, 1, 1);
        BlockPos grinderPos = new BlockPos(2, 1, 1);
        helper.setBlock(reactorPos, io.lakio.krios.industry.registry.ModBlocks.THERMAL_REACTOR.get());
        helper.setBlock(grinderPos, io.lakio.krios.industry.registry.ModBlocks.FRACTAL_GRINDER.get());
        ThermalReactorBlockEntity reactor = helper.getBlockEntity(reactorPos);
        FractalGrinderBlockEntity grinder = helper.getBlockEntity(grinderPos);
        reactor.setItem(ThermalReactorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL_BLOCK));
        grinder.setItem(FractalGrinderBlockEntity.INPUT_FIRST, new ItemStack(Items.COBBLESTONE));
        // The recipe's own pace would be done by now.
        helper.runAtTickTime(150, () -> helper.assertTrue(
                grinder.getItem(FractalGrinderBlockEntity.OUTPUT_FIRST).isEmpty(),
                "off the network the grinder finished before 150 ticks"));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() >= 150, "too early");
            helper.assertTrue(grinder.getItem(FractalGrinderBlockEntity.OUTPUT_FIRST).is(Items.GRAVEL),
                    "no gravel yet");
        });
    }

    /** A tier 2 grinder on a tier 1 core's network works at tier 1: hardware performs, software caps. */
    private static void machineTierCappedByTheCore(GameTestHelper helper) {
        CoreGameTests.buildShell(helper);
        BlockPos conduitPos = new BlockPos(4, 1, 2);
        BlockPos grinderPos = new BlockPos(5, 1, 2);
        helper.setBlock(conduitPos, ModBlocks.NEURAL_CONDUIT.get());
        helper.setBlock(grinderPos, io.lakio.krios.industry.registry.ModBlocks.FRACTAL_GRINDER_TIER2.get());
        helper.succeedWhen(() -> {
            FractalGrinderBlockEntity grinder = helper.getBlockEntity(grinderPos);
            Network network = NetworkManager.networkOf(grinder);
            helper.assertTrue(network != null && network.isValid(), "the grinder is on no valid network");
            helper.assertTrue(network.controller() instanceof KoiosCoreBlockEntity core && core.getTier() == 1,
                    "the network's controller is not a tier 1 core");
            helper.assertValueEqual(grinder.machineTier(), 2, "machine tier");
            helper.assertValueEqual(MachineTiers.effectiveTier(network, grinder.machineTier()), 1, "effective tier");
        });
    }
}
