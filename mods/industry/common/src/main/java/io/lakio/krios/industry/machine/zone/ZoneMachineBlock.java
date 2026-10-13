package io.lakio.krios.industry.machine.zone;

import io.lakio.krios.industry.machine.TieredMachine;
import io.lakio.krios.industry.machine.UpgradeKitItem;
import io.lakio.krios.industry.network.NetworkConductor;
import io.lakio.krios.industry.tool.CalibratorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;

/**
 * Shared zone-machine block. No ticker at all — the only clock is the
 * vanilla scheduler (ADR-022); LIT is purely visual ("worked on the last
 * wake"). Subclasses provide the codec, the block entity and the
 * signature particle color.
 */
public abstract class ZoneMachineBlock extends BaseEntityBlock implements NetworkConductor, TieredMachine {

    private final int tier;
    private final Vector3f particleColor;

    protected ZoneMachineBlock(Properties properties, int tier, Vector3f particleColor) {
        super(properties);
        this.tier = tier;
        this.particleColor = particleColor;
        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    public int machineTier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Screen toward the player; the zone lies behind, where they look.
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

    /** Signature particles (art direction): data bits while working. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BlockStateProperties.LIT) || random.nextInt(3) != 0) {
            return;
        }
        level.addParticle(new DustParticleOptions(particleColor, 0.6F),
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
        if (stack.getItem() instanceof UpgradeKitItem kit) {
            return kit.useOnMachine(stack, level, pos, player);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ZoneMachineBlockEntity machine) {
            player.openMenu(machine);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ZoneMachineBlockEntity machine) {
            Containers.dropContents(level, pos, machine);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** A neighbor changed: flow the buffer out and make sure the chain runs. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ZoneMachineBlockEntity machine) {
            machine.onNeighborChanged();
        }
    }

}
