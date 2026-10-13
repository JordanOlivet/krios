package io.lakio.krios.industry.machine.pod;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.machine.zone.ZoneMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Harvest Pod: the second creature line (wheat-gold data bits). */
public class HarvestPodBlock extends ZoneMachineBlock {

    // The codec is registry plumbing vanilla never uses at runtime; the
    // real tier rides the registered instances.
    private static final MapCodec<HarvestPodBlock> CODEC =
            simpleCodec(properties -> new HarvestPodBlock(properties, 1));

    public HarvestPodBlock(Properties properties, int tier) {
        super(properties, tier, new Vector3f(0.95F, 0.78F, 0.35F));
    }

    @Override
    protected MapCodec<HarvestPodBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HarvestPodBlockEntity(pos, state);
    }
}
