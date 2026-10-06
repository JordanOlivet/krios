package io.lakio.krios.industry.machine.grinder;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.network.NetworkConductor;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.tool.CalibratorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class FractalGrinderBlock extends BaseEntityBlock
        implements NetworkConductor, io.lakio.krios.industry.machine.TieredMachine {

    // The codec is registry plumbing vanilla never uses at runtime; the
    // real tier rides the registered instances.
    private static final MapCodec<FractalGrinderBlock> CODEC =
            simpleCodec(properties -> new FractalGrinderBlock(properties, 1));

    private final int tier;

    public FractalGrinderBlock(Properties properties, int tier) {
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
    protected MapCodec<FractalGrinderBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Front (screen side) toward the player.
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

    /** Signature particles (art direction): cyan data bits while grinding. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BlockStateProperties.LIT) || random.nextInt(3) != 0) {
            return;
        }
        level.addParticle(new DustParticleOptions(new Vector3f(0.19F, 0.85F, 1F), 0.6F),
                pos.getX() + 0.2 + random.nextDouble() * 0.6,
                pos.getY() + 1.05,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                0, 0.02, 0);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The Calibrator overrides the block's own right-click (diagnostic). */
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
        return new FractalGrinderBlockEntity(pos, state);
    }

    // LIT = has work queued (input present, output not full). An energy-starved
    // grinder stays lit and polls its neighbors — accepted O(1) cost until a
    // real energy network brings scheduled wake-ups (ADR-012 note).
    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(BlockStateProperties.LIT)) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.FRACTAL_GRINDER.get(), FractalGrinderBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FractalGrinderBlockEntity grinder) {
            player.openMenu(grinder);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FractalGrinderBlockEntity grinder) {
            Containers.dropContents(level, pos, grinder);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Every significant neighborhood change re-evaluates the machine. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.level.block.Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FractalGrinderBlockEntity grinder) {
            grinder.onNeighborChanged();
        }
    }

    /** Scheduled wake-up (ADR-017): service the manual item faces. */
    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof FractalGrinderBlockEntity grinder) {
            grinder.scheduledWake();
        }
    }
}
