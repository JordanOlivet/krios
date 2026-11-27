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
                        IndustryGameTests::absentMaterialRecipesAreUnavailable),
                new TestFunction(BATCH, "ores_grind_straight_into_dusts", CoreGameTests.ARENA, 20, 0L, true,
                        IndustryGameTests::oresGrindStraightIntoDusts),
                new TestFunction(BATCH, "modded_ore_falls_back_to_its_raw_metal", CoreGameTests.ARENA, 20, 0L, true,
                        IndustryGameTests::moddedOreFallsBackToItsRawMetal),
                new TestFunction(BATCH, "grinder_counts_yield_up_to_its_tier_cap", CoreGameTests.ARENA, 600, 0L, true,
                        IndustryGameTests::grinderCountsYieldUpToItsTierCap),
                new TestFunction(BATCH, "module_bay_refuses_yield_past_the_cap", CoreGameTests.ARENA, 200, 0L, true,
                        IndustryGameTests::moduleBayRefusesYieldPastTheCap),
                new TestFunction(BATCH, "zone_machine_counts_yield_up_to_its_tier_cap", CoreGameTests.ARENA, 200, 0L,
                        true, IndustryGameTests::zoneMachineCountsYieldUpToItsTierCap),
                new TestFunction(BATCH, "grinder_off_network_gives_exactly_half", CoreGameTests.ARENA, 900, 0L, true,
                        IndustryGameTests::grinderOffNetworkGivesExactlyHalf),
                new TestFunction(BATCH, "plasma_forge_smelts_one_for_one", CoreGameTests.ARENA, 600, 0L, true,
                        IndustryGameTests::plasmaForgeSmeltsOneForOne));
    }

    // --- The yield pass (ADR-038) ---

    private static final BlockPos NETWORK_MACHINE = new BlockPos(5, 1, 2);
    private static final BlockPos NETWORK_REACTOR = new BlockPos(6, 1, 2);

    /** A machine beside a reactor, joined by a conduit to a formed tier 1 core. */
    private static void placeOnNetwork(GameTestHelper helper, net.minecraft.world.level.block.Block machine) {
        CoreGameTests.buildShell(helper);
        helper.setBlock(new BlockPos(4, 1, 2), ModBlocks.NEURAL_CONDUIT.get());
        helper.setBlock(NETWORK_MACHINE, machine);
        helper.setBlock(NETWORK_REACTOR, io.lakio.krios.industry.registry.ModBlocks.THERMAL_REACTOR.get());
    }

    private static void assertOnline(GameTestHelper helper, net.minecraft.world.level.block.entity.BlockEntity be) {
        Network network = NetworkManager.networkOf((io.lakio.krios.core.network.NetworkNode) be);
        helper.assertTrue(network != null && network.isValid(), "the machine is on no valid network");
    }

    private static ItemStack yieldModule(boolean mk2) {
        return new ItemStack(mk2 ? io.lakio.krios.industry.registry.ModItems.YIELD_MODULE_MK2.get()
                : io.lakio.krios.industry.registry.ModItems.YIELD_MODULE.get());
    }

    /** A metal ore gives its dusts in one step; ancient debris and nether gold ore grind like the rest. */
    private static void oresGrindStraightIntoDusts(GameTestHelper helper) {
        record Expected(String recipe, Item item, int count) {
        }
        for (Expected expected : List.of(
                new Expected("iron_ore", ModItems.IRON_DUST.get(), 2),
                new Expected("gold_ore", ModItems.GOLD_DUST.get(), 2),
                new Expected("copper_ore", ModItems.COPPER_DUST.get(), 7),
                new Expected("neurite_ore", ModItems.NEURITE_DUST.get(), 2),
                new Expected("ancient_debris", Items.NETHERITE_SCRAP, 2))) {
            GrindingRecipe recipe = grinding(helper, expected.recipe());
            helper.assertTrue(recipe.available(), "grinding_" + expected.recipe() + " is unavailable");
            ItemStack result = recipe.result();
            helper.assertTrue(result.is(expected.item()) && result.getCount() == expected.count(),
                    "grinding_" + expected.recipe() + " gives " + result);
        }
        helper.assertTrue(grinding(helper, "ancient_debris")
                .matches(new SingleRecipeInput(new ItemStack(Items.ANCIENT_DEBRIS)), helper.getLevel()),
                "ancient debris does not match its recipe");
        helper.assertTrue(grinding(helper, "gold_ore")
                .matches(new SingleRecipeInput(new ItemStack(Items.NETHER_GOLD_ORE)), helper.getLevel()),
                "nether gold ore does not match the gold ore recipe");
        helper.succeed();
    }

    /** A result list gives its first entry that has an item: the dust, else the raw metal. */
    private static void moddedOreFallsBackToItsRawMetal(GameTestHelper helper) {
        GrindingRecipe noDust = new GrindingRecipe(Ingredient.of(Items.IRON_ORE), List.of(
                Either.right(new GrindingRecipe.TagResult(itemTag("dusts/tin"), 2)),
                Either.right(new GrindingRecipe.TagResult(itemTag("raw_materials/iron"), 2))), 800, 160);
        helper.assertTrue(noDust.available(), "a recipe whose second entry has an item is unavailable");
        helper.assertTrue(noDust.result().is(Items.RAW_IRON) && noDust.result().getCount() == 2,
                "with no tin dust the recipe gave " + noDust.result());
        GrindingRecipe withDust = new GrindingRecipe(Ingredient.of(Items.IRON_ORE), List.of(
                Either.right(new GrindingRecipe.TagResult(itemTag("dusts/iron"), 2)),
                Either.right(new GrindingRecipe.TagResult(itemTag("raw_materials/iron"), 2))), 800, 160);
        helper.assertTrue(withDust.result().is(ModItems.IRON_DUST.get()),
                "with an iron dust the recipe gave " + withDust.result());
        helper.succeed();
    }

    /** Four Yield points in a tier 1 grinder count two: one raw iron gives three dusts, not four. */
    private static void grinderCountsYieldUpToItsTierCap(GameTestHelper helper) {
        placeOnNetwork(helper, io.lakio.krios.industry.registry.ModBlocks.FRACTAL_GRINDER.get());
        FractalGrinderBlockEntity grinder = helper.getBlockEntity(NETWORK_MACHINE);
        ThermalReactorBlockEntity reactor = helper.getBlockEntity(NETWORK_REACTOR);
        helper.startSequence()
                .thenWaitUntil(() -> assertOnline(helper, grinder))
                .thenExecute(() -> {
                    grinder.setItem(FractalGrinderBlockEntity.MODULE_SLOT_FIRST, yieldModule(true));
                    grinder.setItem(FractalGrinderBlockEntity.MODULE_SLOT_FIRST + 1, yieldModule(true));
                    reactor.setItem(ThermalReactorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL_BLOCK));
                    grinder.setItem(FractalGrinderBlockEntity.INPUT_FIRST, new ItemStack(Items.RAW_IRON));
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        grinder.getItem(FractalGrinderBlockEntity.INPUT_FIRST).isEmpty(), "the raw iron is not ground"))
                .thenExecute(() -> {
                    ItemStack out = grinder.getItem(FractalGrinderBlockEntity.OUTPUT_FIRST);
                    helper.assertTrue(out.is(ModItems.IRON_DUST.get()), "the output slot holds " + out);
                    helper.assertValueEqual(out.getCount(), 3, "iron dusts from one raw iron at the tier 1 cap");
                })
                .thenSucceed();
    }

    /**
     * The bay refuses a Yield module past the tier's cap, before the KOIOS
     * gate: at level 4 a Yield I goes in beside a Yield I, not beside a Mk II.
     */
    private static void moduleBayRefusesYieldPastTheCap(GameTestHelper helper) {
        placeOnNetwork(helper, io.lakio.krios.industry.registry.ModBlocks.FRACTAL_GRINDER.get());
        FractalGrinderBlockEntity grinder = helper.getBlockEntity(NETWORK_MACHINE);
        helper.startSequence()
                .thenWaitUntil(() -> assertOnline(helper, grinder))
                .thenExecute(() -> {
                    KoiosCoreBlockEntity core = helper.getBlockEntity(CoreGameTests.CORE);
                    core.setXpAdmin(io.lakio.krios.core.koios.KoiosBalance.LEVEL_XP[
                            io.lakio.krios.industry.IndustryBalance.YIELD_UNLOCK_LEVEL]);
                    net.minecraft.world.entity.player.Player player =
                            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                    var menu = grinder.createMenu(0, player.getInventory(), player);
                    var secondBaySlot = menu.getSlot(FractalGrinderBlockEntity.MODULE_SLOT_FIRST + 1);

                    grinder.setItem(FractalGrinderBlockEntity.MODULE_SLOT_FIRST, yieldModule(false));
                    helper.assertTrue(secondBaySlot.mayPlace(yieldModule(false)),
                            "a second Yield I is refused in a tier 1 bay at level 4");
                    grinder.setItem(FractalGrinderBlockEntity.MODULE_SLOT_FIRST, yieldModule(true));
                    helper.assertTrue(!secondBaySlot.mayPlace(yieldModule(false)),
                            "a Yield I goes in beside a Yield II, past the tier 1 cap");
                })
                .thenSucceed();
    }

    /** Zone machines count Yield the same way: four points in a tier 1 Harvest Pod count two. */
    private static void zoneMachineCountsYieldUpToItsTierCap(GameTestHelper helper) {
        placeOnNetwork(helper, io.lakio.krios.industry.registry.ModBlocks.HARVEST_POD.get());
        io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity pod = helper.getBlockEntity(NETWORK_MACHINE);
        int firstModule = pod.getContainerSize()
                - io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity.MODULE_SLOT_COUNT;
        helper.startSequence()
                .thenWaitUntil(() -> assertOnline(helper, pod))
                .thenExecute(() -> {
                    pod.setItem(firstModule, yieldModule(true));
                    pod.setItem(firstModule + 1, yieldModule(true));
                    helper.assertValueEqual(pod.countedYieldPoints(), 2, "Yield points a tier 1 pod counts");
                })
                .thenSucceed();
    }

    /** Off a network a grinder gives exactly half: two copper ores give seven dusts, vanilla's average. */
    private static void grinderOffNetworkGivesExactlyHalf(GameTestHelper helper) {
        BlockPos reactorPos = new BlockPos(1, 1, 1);
        BlockPos grinderPos = new BlockPos(2, 1, 1);
        helper.setBlock(reactorPos, io.lakio.krios.industry.registry.ModBlocks.THERMAL_REACTOR.get());
        helper.setBlock(grinderPos, io.lakio.krios.industry.registry.ModBlocks.FRACTAL_GRINDER.get());
        ThermalReactorBlockEntity reactor = helper.getBlockEntity(reactorPos);
        FractalGrinderBlockEntity grinder = helper.getBlockEntity(grinderPos);
        reactor.setItem(ThermalReactorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL_BLOCK));
        grinder.setItem(FractalGrinderBlockEntity.INPUT_FIRST, new ItemStack(Items.COPPER_ORE, 2));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        grinder.getItem(FractalGrinderBlockEntity.INPUT_FIRST).isEmpty(), "the ores are not ground"))
                .thenExecute(() -> {
                    ItemStack out = grinder.getItem(FractalGrinderBlockEntity.OUTPUT_FIRST);
                    helper.assertTrue(out.is(ModItems.COPPER_DUST.get()), "the output slot holds " + out);
                    helper.assertValueEqual(out.getCount(), 7, "copper dusts from two ores off the network");
                })
                .thenSucceed();
    }

    /** The forge smelts one dust into one ingot, Yield modules in its bay or not. */
    private static void plasmaForgeSmeltsOneForOne(GameTestHelper helper) {
        placeOnNetwork(helper, io.lakio.krios.industry.registry.ModBlocks.PLASMA_FORGE.get());
        io.lakio.krios.industry.machine.forge.PlasmaForgeBlockEntity forge = helper.getBlockEntity(NETWORK_MACHINE);
        ThermalReactorBlockEntity reactor = helper.getBlockEntity(NETWORK_REACTOR);
        int bay = io.lakio.krios.industry.machine.forge.PlasmaForgeBlockEntity.MODULE_SLOT_FIRST;
        int input = io.lakio.krios.industry.machine.forge.PlasmaForgeBlockEntity.INPUT_FIRST;
        int output = io.lakio.krios.industry.machine.forge.PlasmaForgeBlockEntity.OUTPUT_FIRST;
        helper.startSequence()
                .thenWaitUntil(() -> assertOnline(helper, forge))
                .thenExecute(() -> {
                    forge.setItem(bay, yieldModule(true));
                    forge.setItem(bay + 1, yieldModule(true));
                    reactor.setItem(ThermalReactorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL_BLOCK));
                    forge.setItem(input, new ItemStack(ModItems.IRON_DUST.get(), 2));
                })
                .thenWaitUntil(() -> helper.assertTrue(forge.getItem(input).isEmpty(), "the dusts are not smelted"))
                .thenExecute(() -> {
                    ItemStack out = forge.getItem(output);
                    helper.assertTrue(out.is(Items.IRON_INGOT), "the output slot holds " + out);
                    helper.assertValueEqual(out.getCount(), 2, "iron ingots from two dusts");
                })
                .thenSucceed();
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
