package io.lakio.krios.industry.machine.lure;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.machine.zone.ZoneMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Quantum Lure: the third zone machine (deep-water blue data bits). */
public class QuantumLureBlock extends ZoneMachineBlock {

    // The codec is registry plumbing vanilla never uses at runtime; the
    // real tier rides the registered instances.
    private static final MapCodec<QuantumLureBlock> CODEC =
            simpleCodec(properties -> new QuantumLureBlock(properties, 1));

    public QuantumLureBlock(Properties properties, int tier) {
        super(properties, tier, new Vector3f(0.25F, 0.55F, 0.95F));
    }

    @Override
    protected MapCodec<QuantumLureBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumLureBlockEntity(pos, state);
    }
}
