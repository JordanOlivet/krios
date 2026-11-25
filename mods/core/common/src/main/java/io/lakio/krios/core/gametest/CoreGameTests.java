package io.lakio.krios.core.gametest;

import java.util.List;

import io.lakio.krios.core.koios.KoiosCoreBlock;
import io.lakio.krios.core.koios.NeuralCasingBlock;
import io.lakio.krios.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;

/**
 * Core's in-world tests (ADR-035): the vanilla GameTest framework, one test
 * per rule, each in its own empty arena. Registered by each loader (NeoForge's
 * RegisterGameTestsEvent, Fabric's {@code fabric-gametest} entrypoint), run
 * by the game test servers (`gradlew neoforge:runGameTestServer`,
 * `gradlew fabric:runGametest`, or tools/acceptance/gametest.py). A
 * generator, not annotated methods, so the class stays plain vanilla in
 * common: the loaders' own annotations are never needed.
 */
public class CoreGameTests {

    /** An empty 8x8x8 arena, shipped in core's data for every Krios mod's tests. */
    public static final String ARENA = "krios_core:gametest/empty";

    private static final String BATCH = "krios_core";

    /** The KOIOS shell used by the tests: casings 1..3 on each axis, the core in the middle. */
    public static final BlockPos CORE = new BlockPos(2, 2, 2);

    @GameTestGenerator
    public List<TestFunction> tests() {
        return List.of(
                new TestFunction(BATCH, "koios_shell_forms", ARENA, 100, 0L, true, CoreGameTests::shellForms),
                new TestFunction(BATCH, "koios_shell_breaks", ARENA, 100, 0L, true, CoreGameTests::shellBreaks));
    }

    /**
     * A core, then the casings around it: the last casing completes the shell
     * and its placement makes the core check it. (A core placed last by code
     * rather than by a player has no placer hook: it checks on its first
     * GUI opening.)
     */
    public static void buildShell(GameTestHelper helper) {
        helper.setBlock(CORE, ModBlocks.KOIOS_CORE.get());
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    if (x != 2 || y != 2 || z != 2) {
                        helper.setBlock(x, y, z, ModBlocks.NEURAL_CASING.get());
                    }
                }
            }
        }
    }

    private static void shellForms(GameTestHelper helper) {
        buildShell(helper);
        helper.succeedWhen(() -> {
            helper.assertBlockProperty(CORE, KoiosCoreBlock.FORMED, true);
            helper.assertBlockProperty(new BlockPos(1, 1, 1), NeuralCasingBlock.FORMED, true);
        });
    }

    /** A casing taken out of a formed shell: the core and the casings let go. */
    private static void shellBreaks(GameTestHelper helper) {
        buildShell(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(CORE, KoiosCoreBlock.FORMED, true))
                .thenExecute(() -> helper.setBlock(3, 2, 2, Blocks.AIR))
                .thenWaitUntil(() -> {
                    helper.assertBlockProperty(CORE, KoiosCoreBlock.FORMED, false);
                    helper.assertBlockProperty(new BlockPos(1, 1, 1), NeuralCasingBlock.FORMED, false);
                })
                .thenSucceed();
    }
}
