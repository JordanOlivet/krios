package io.lakio.krios.industry.cable;

import java.util.Map;

import io.lakio.krios.industry.network.NetworkConductor;
import io.lakio.krios.industry.network.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tier 1 universal conduit: a pure network conductor bridging gaps between
 * machines (design: one cable carries everything; transport itself is
 * pooled at the network level). No block entity — place/remove hooks feed
 * the graph; the connection properties are purely cosmetic and must never
 * touch the graph (same-block guard in onPlace).
 */
public class NeuralConduitBlock extends Block implements NetworkConductor {

    public static final Map<Direction, BooleanProperty> CONNECTIONS = PipeBlock.PROPERTY_BY_DIRECTION;

    // 4 px profile (graphics pass): 6 px read too bulky in-world.
    private static final VoxelShape CORE_SHAPE = Block.box(6, 6, 6, 10, 10, 10);
    /** Indexed by Direction.get3DDataValue(): D, U, N, S, W, E. */
    private static final VoxelShape[] ARM_SHAPES = {
            Block.box(6, 0, 6, 10, 6, 10),
            Block.box(6, 10, 6, 10, 16, 10),
            Block.box(6, 6, 0, 10, 10, 6),
            Block.box(6, 6, 10, 10, 10, 16),
            Block.box(0, 6, 6, 6, 10, 10),
            Block.box(10, 6, 6, 16, 10, 10)};
    private final VoxelShape[] shapes = new VoxelShape[64];

    public NeuralConduitBlock(Properties properties) {
        super(properties);
        BlockState state = getStateDefinition().any();
        for (BooleanProperty property : CONNECTIONS.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
    }

    private static boolean connects(BlockState neighbor) {
        return neighbor.getBlock() instanceof NetworkConductor;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(direction),
                    connects(level.getBlockState(pos.relative(direction))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(CONNECTIONS.get(direction), connects(neighborState));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int bits = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(direction))) {
                bits |= 1 << direction.get3DDataValue();
            }
        }
        VoxelShape shape = shapes[bits];
        if (shape == null) {
            shape = CORE_SHAPE;
            for (Direction direction : Direction.values()) {
                if ((bits & 1 << direction.get3DDataValue()) != 0) {
                    shape = Shapes.or(shape, ARM_SHAPES[direction.get3DDataValue()]);
                }
            }
            shapes[bits] = shape;
        }
        return shape;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            NetworkManager.conductorChanged(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean gone = !state.is(newState.getBlock());
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (gone) {
            NetworkManager.conductorChanged(level, pos);
        }
    }
}
