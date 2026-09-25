package io.lakio.krios.industry.machine.crusher;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.network.NetworkConductor;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class CrusherBlock extends BaseEntityBlock implements NetworkConductor {

    private static final MapCodec<CrusherBlock> CODEC = simpleCodec(CrusherBlock::new);

    public CrusherBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected MapCodec<CrusherBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrusherBlockEntity(pos, state);
    }

    // LIT = has work queued (input present, output not full). An energy-starved
    // crusher stays lit and polls its neighbors — accepted O(1) cost until a
    // real energy network brings scheduled wake-ups (ADR-012 note).
    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(BlockStateProperties.LIT)) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CRUSHER.get(), CrusherBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CrusherBlockEntity crusher) {
            player.openMenu(crusher);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CrusherBlockEntity crusher) {
            Containers.dropContents(level, pos, crusher);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
