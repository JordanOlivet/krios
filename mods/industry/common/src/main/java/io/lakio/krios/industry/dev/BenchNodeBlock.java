package io.lakio.krios.industry.dev;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/** Benchmark node: the blockstate carries the mode, so getTicker decides like a real machine. */
public class BenchNodeBlock extends BaseEntityBlock {

    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, BenchMode.VALUES.length - 1);
    private static final MapCodec<BenchNodeBlock> CODEC = simpleCodec(BenchNodeBlock::new);

    public BenchNodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BenchNodeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide || type != DevBench.NODE_BE.get()) {
            return null;
        }
        return switch (BenchMode.VALUES[state.getValue(MODE)]) {
            case TICKER_EMPTY -> (l, p, s, be) -> { };
            case TICKER_WORK -> (l, p, s, be) -> ((BenchNodeBlockEntity) be).tickWork();
            case TICKER_DIRTY -> (l, p, s, be) -> {
                BenchNodeBlockEntity node = (BenchNodeBlockEntity) be;
                node.tickWork();
                node.setChanged();
            };
            default -> null;
        };
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (BenchMode.VALUES[state.getValue(MODE)] == BenchMode.SCHEDULED
                && level.getBlockEntity(pos) instanceof BenchNodeBlockEntity node) {
            node.batch();
            level.scheduleTick(pos, this, BenchNodeBlockEntity.BATCH_EVERY);
        }
    }
}
