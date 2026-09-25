package io.lakio.krios.industry.koios;

import io.lakio.krios.industry.network.NetworkConductor;
import io.lakio.krios.industry.network.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Shell block of the KOIOS core multiblock. No block entity; placing or
 * removing one pokes any core within one block so the structure revalidates
 * event-driven (every shell block sits within the core's 3x3x3 shell).
 *
 * Once formed, the structure acts as one entity: the core flips FORMED on
 * all 26 casings (seamless texture, the shell reads as a single big block)
 * and right-clicking any of them opens the core's UI.
 */
public class NeuralCasingBlock extends Block implements NetworkConductor {

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public NeuralCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        KoiosCoreBlockEntity core = findFormedCore(level, pos);
        if (core == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.openMenu(core);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Property flips (the core forming/unforming the shell) must not
        // re-trigger validation, only real placements.
        if (!oldState.is(this)) {
            notifyCores(level, pos);
            NetworkManager.conductorChanged(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean gone = !state.is(newState.getBlock());
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (gone) {
            notifyCores(level, pos);
            NetworkManager.conductorChanged(level, pos);
        }
    }

    @Nullable
    public static KoiosCoreBlockEntity findFormedCore(Level level, BlockPos pos) {
        for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getBlockEntity(cursor) instanceof KoiosCoreBlockEntity core && core.isFormed()) {
                return core;
            }
        }
        return null;
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
