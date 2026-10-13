package io.lakio.krios.industry.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A machine-shaped workload: a cheap per-tick check, and every 10 ticks a
 * batch (one neighbor lookup, a small primitive loop, setChanged) — the
 * shape of a zone machine's wake.
 */
public class BenchNodeBlockEntity extends BlockEntity {

    public static final int BATCH_EVERY = 10;

    private final int[] data = new int[16];
    private int counter;
    /** Sink for the batch result, so the JIT cannot drop the loop. */
    public static long sink;

    public BenchNodeBlockEntity(BlockPos pos, BlockState state) {
        super(DevBench.NODE_BE.get(), pos, state);
    }

    public void tickWork() {
        if (++counter % BATCH_EVERY == 0) {
            batch();
        }
    }

    public void batch() {
        if (level == null) {
            return;
        }
        BlockEntity neighbor = level.getBlockEntity(worldPosition.east());
        long sum = neighbor != null ? 1 : 0;
        for (int i = 0; i < data.length; i++) {
            data[i] += i;
            sum += data[i];
        }
        sink += sum;
        setChanged();
    }
}
