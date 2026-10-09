package io.lakio.krios.industry.machine.pod;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.machine.zone.ZoneMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Breeding Pod: the first creature line (warm rose data bits). */
public class BreedingPodBlock extends ZoneMachineBlock {

    // The codec is registry plumbing vanilla never uses at runtime; the
    // real tier rides the registered instances.
    private static final MapCodec<BreedingPodBlock> CODEC =
            simpleCodec(properties -> new BreedingPodBlock(properties, 1));

    public BreedingPodBlock(Properties properties, int tier) {
        super(properties, tier, new Vector3f(0.95F, 0.45F, 0.6F));
    }

    @Override
    protected MapCodec<BreedingPodBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreedingPodBlockEntity(pos, state);
    }
}
