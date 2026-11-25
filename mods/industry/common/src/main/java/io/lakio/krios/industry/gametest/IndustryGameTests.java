package io.lakio.krios.industry.gametest;

import java.util.List;

import io.lakio.krios.core.gametest.CoreGameTests;
import io.lakio.krios.core.koios.KoiosCoreBlockEntity;
import io.lakio.krios.core.network.Network;
import io.lakio.krios.core.network.NetworkManager;
import io.lakio.krios.core.registry.ModBlocks;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.grinder.FractalGrinderBlockEntity;
import io.lakio.krios.industry.machine.reactor.ThermalReactorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
                        IndustryGameTests::machineTierCappedByTheCore));
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
