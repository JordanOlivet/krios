package io.lakio.krios.industry.machine.forester;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.machine.zone.ZoneMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Cyclic Forester: the second zone machine (sap-green data bits). */
public class CyclicForesterBlock extends ZoneMachineBlock {

    // The codec is registry plumbing vanilla never uses at runtime; the
    // real tier rides the registered instances.
    private static final MapCodec<CyclicForesterBlock> CODEC =
            simpleCodec(properties -> new CyclicForesterBlock(properties, 1));

    public CyclicForesterBlock(Properties properties, int tier) {
        super(properties, tier, new Vector3f(0.55F, 0.85F, 0.35F));
    }

    @Override
    protected MapCodec<CyclicForesterBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CyclicForesterBlockEntity(pos, state);
    }
}
