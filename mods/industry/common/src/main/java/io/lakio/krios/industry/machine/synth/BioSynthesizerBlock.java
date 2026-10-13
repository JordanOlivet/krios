package io.lakio.krios.industry.machine.synth;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.machine.zone.ZoneMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Bio Synthesizer: the spawner line (bio-green data bits). */
public class BioSynthesizerBlock extends ZoneMachineBlock {

    // The codec is registry plumbing vanilla never uses at runtime; the
    // real tier rides the registered instances.
    private static final MapCodec<BioSynthesizerBlock> CODEC =
            simpleCodec(properties -> new BioSynthesizerBlock(properties, 1));

    public BioSynthesizerBlock(Properties properties, int tier) {
        super(properties, tier, new Vector3f(0.45F, 0.95F, 0.55F));
    }

    @Override
    protected MapCodec<BioSynthesizerBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BioSynthesizerBlockEntity(pos, state);
    }
}
