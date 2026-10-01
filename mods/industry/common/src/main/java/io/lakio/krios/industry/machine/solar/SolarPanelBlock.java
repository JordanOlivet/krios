package io.lakio.krios.industry.machine.solar;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.network.NetworkConductor;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.tool.CalibratorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Full machine block, the photovoltaic deck as its top face (revised
 * 2026-10-01: machines stay near-block-size, and a full flank meets the
 * conduit arm cleanly). LIT = producing: the ticker only exists while
 * lit (ADR-012); dark panels live on the slow poll chain (ADR-017)
 * driven by {@link #tick}.
 */
public class SolarPanelBlock extends BaseEntityBlock implements NetworkConductor {

    private static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);

    public SolarPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected MapCodec<SolarPanelBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LIT);
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
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SolarPanelBlockEntity panel) {
            player.openMenu(panel);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(BlockStateProperties.LIT)) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.SOLAR_PANEL.get(), SolarPanelBlockEntity::serverTick);
    }

    /** Enter the poll chain on placement (next tick, once the BE exists). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** Slow poll wake-up (ADR-017): the dark panel asks "can I produce?". */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SolarPanelBlockEntity panel) {
            panel.pollWake();
        }
    }

    /** A drained neighbor or cleared roof may free us before the next poll. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SolarPanelBlockEntity panel) {
            panel.tryResume();
        }
    }
}
