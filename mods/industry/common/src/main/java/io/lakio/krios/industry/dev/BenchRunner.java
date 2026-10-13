package io.lakio.krios.industry.dev;

import java.util.ArrayList;
import java.util.List;

import io.lakio.krios.industry.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Drives a benchmark suite, one mode after the other, from the controller
 * block's ticker: place N nodes in a flat grid high above the source
 * (chunks force-loaded for the run), warm up 100 ticks, sample the server's
 * 100-tick average six times, clear, next mode. Results go to the log and
 * the chat as mean mspt, the delta over the baseline, and the cost per node.
 */
public final class BenchRunner {

    private static final int WARMUP_TICKS = 100;
    private static final int SAMPLES = 6;
    private static final int SAMPLE_EVERY = 100;

    private static boolean running;
    private static ServerLevel level;
    private static BlockPos origin;
    private static int count;
    private static int side;
    private static List<BenchMode> queue;
    private static int modeIndex;
    private static int phaseTick;
    private static long sampleSum;
    private static int sampleCount;
    private static double baselineMs = -1;
    private static final List<String> results = new ArrayList<>();
    private static final List<BenchNodeBlockEntity> managed = new ArrayList<>();

    private BenchRunner() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static void start(ServerLevel target, BlockPos at, int nodes, List<BenchMode> modes) {
        running = true;
        level = target;
        count = nodes;
        side = (int) Math.ceil(Math.sqrt(nodes));
        origin = new BlockPos(at.getX(), target.getMaxBuildHeight() - 16, at.getZ());
        queue = modes;
        modeIndex = 0;
        baselineMs = -1;
        results.clear();
        forceChunks(true);
        level.setBlock(origin, DevBench.CONTROLLER.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        place(queue.get(0));
        say("Bench: " + nodes + " nodes, " + modes.size() + " modes, ~"
                + modes.size() * (WARMUP_TICKS + SAMPLES * SAMPLE_EVERY) / 20 + " s");
    }

    public static void stop() {
        if (!running) {
            return;
        }
        clearNodes();
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        forceChunks(false);
        running = false;
    }

    /** Called once per server tick by the controller block entity. */
    public static void tick() {
        if (!running) {
            return;
        }
        BenchMode mode = queue.get(modeIndex);
        if (mode == BenchMode.MANAGED) {
            for (int i = 0, n = managed.size(); i < n; i++) {
                managed.get(i).tickWork();
            }
        }
        phaseTick++;
        if (phaseTick <= WARMUP_TICKS || (phaseTick - WARMUP_TICKS) % SAMPLE_EVERY != 0) {
            return;
        }
        sampleSum += level.getServer().getAverageTickTimeNanos();
        if (++sampleCount < SAMPLES) {
            return;
        }
        double ms = sampleSum / (double) sampleCount / 1_000_000.0;
        if (mode == BenchMode.BASELINE) {
            baselineMs = ms;
        }
        String line = baselineMs < 0 || mode == BenchMode.BASELINE
                ? String.format("%-13s %.3f mspt", mode, ms)
                : String.format("%-13s %.3f mspt  (+%.3f ms, %.3f us/node)", mode, ms,
                        ms - baselineMs, (ms - baselineMs) * 1000.0 / count);
        results.add(line);
        say(line);
        clearNodes();
        modeIndex++;
        if (modeIndex >= queue.size()) {
            Constants.LOG.info("Bench results ({} nodes):\n{}", count, String.join("\n", results));
            say("Bench done.");
            level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            forceChunks(false);
            running = false;
            return;
        }
        place(queue.get(modeIndex));
    }

    private static BlockPos nodePos(int i) {
        return origin.offset(1 + i % side, 0, i / side);
    }

    private static void place(BenchMode mode) {
        phaseTick = 0;
        sampleSum = 0;
        sampleCount = 0;
        managed.clear();
        Block node = DevBench.NODE.get();
        for (int i = 0; i < count; i++) {
            BlockPos pos = nodePos(i);
            level.setBlock(pos, node.defaultBlockState().setValue(BenchNodeBlock.MODE, mode.ordinal()),
                    Block.UPDATE_CLIENTS);
            if (mode == BenchMode.SCHEDULED) {
                // Spread the wakes the way independent machines would be.
                level.scheduleTick(pos, node, 1 + i % BenchNodeBlockEntity.BATCH_EVERY);
            } else if (mode == BenchMode.MANAGED
                    && level.getBlockEntity(pos) instanceof BenchNodeBlockEntity be) {
                managed.add(be);
            }
        }
    }

    private static void clearNodes() {
        managed.clear();
        for (int i = 0; i < count; i++) {
            level.setBlock(nodePos(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void forceChunks(boolean force) {
        int minX = origin.getX() >> 4;
        int maxX = (origin.getX() + side + 1) >> 4;
        int minZ = origin.getZ() >> 4;
        int maxZ = (origin.getZ() + side + 1) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setChunkForced(x, z, force);
            }
        }
    }

    private static void say(String message) {
        Constants.LOG.info(message);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }
}
