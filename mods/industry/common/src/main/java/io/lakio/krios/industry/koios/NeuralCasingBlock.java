package io.lakio.krios.industry.koios;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Frame block of the KOIOS core multiblock. No block entity; placing or
 * removing one pokes any core within one block so the structure revalidates
 * event-driven (a frame block is always within the core's 3x3x3 shell).
 */
public class NeuralCasingBlock extends Block {

    public NeuralCasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        notifyCores(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean gone = !state.is(newState.getBlock());
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (gone) {
            notifyCores(level, pos);
        }
    }

    private static void notifyCores(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getBlockEntity(cursor) instanceof KoiosCoreBlockEntity core) {
                core.revalidateStructure();
            }
        }
    }
}
