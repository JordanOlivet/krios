package io.lakio.krios.industry.machine.field;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.machine.zone.ZoneMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Phase Death Field: the killing line (violet data bits). */
public class PhaseDeathFieldBlock extends ZoneMachineBlock {

    // The codec is registry plumbing vanilla never uses at runtime; the
    // real tier rides the registered instances.
    private static final MapCodec<PhaseDeathFieldBlock> CODEC =
            simpleCodec(properties -> new PhaseDeathFieldBlock(properties, 1));

    public PhaseDeathFieldBlock(Properties properties, int tier) {
        super(properties, tier, new Vector3f(0.7F, 0.4F, 1.0F));
    }

    @Override
    protected MapCodec<PhaseDeathFieldBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhaseDeathFieldBlockEntity(pos, state);
    }
}
