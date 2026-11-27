package io.lakio.krios.industry.machine.magma;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.core.network.NetworkConductor;
import io.lakio.krios.core.tool.CalibratorItem;
import io.lakio.krios.industry.machine.TieredMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;

/** The Magma Reactor block: a tier 2 line from its first version (ADR-020). */
public class MagmaReactorBlock extends BaseEntityBlock implements NetworkConductor, TieredMachine {

    // Registry plumbing vanilla never uses at runtime; the real tier rides
    // the registered instances.
    private static final MapCodec<MagmaReactorBlock> CODEC =
            simpleCodec(properties -> new MagmaReactorBlock(properties, 2));

    private final int tier;

    public MagmaReactorBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    public int machineTier() {
        return tier;
    }

    @Override
    protected MapCodec<MagmaReactorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    /** Signature particles: orange embers while burning, now and then a lava drip pop. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BlockStateProperties.LIT)) {
            return;
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(new DustParticleOptions(new Vector3f(1F, 0.42F, 0.12F), 0.8F),
                    pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 1.02,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                    0, 0.02, 0);
        }
        if (random.nextInt(40) == 0) {
            level.addParticle(ParticleTypes.LAVA,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0, 0);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The Calibrator and upgrade kits override the block's own right-click. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof CalibratorItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof io.lakio.krios.industry.machine.UpgradeKitItem kit) {
            return kit.useOnMachine(stack, level, pos, player);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MagmaReactorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MagmaReactorBlockEntity reactor) {
            player.openMenu(reactor);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MagmaReactorBlockEntity reactor) {
            Containers.dropContents(level, pos, reactor);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Every significant neighborhood change re-evaluates the machine. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MagmaReactorBlockEntity reactor) {
            reactor.onNeighborChanged();
        }
    }
}
