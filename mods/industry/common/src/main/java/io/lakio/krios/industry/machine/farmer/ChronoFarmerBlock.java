package io.lakio.krios.industry.machine.farmer;

import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.machine.zone.ZoneMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Chrono Farmer: the first zone machine (green-cyan data bits). */
public class ChronoFarmerBlock extends ZoneMachineBlock {

    // The codec is registry plumbing vanilla never uses at runtime; the
    // real tier rides the registered instances.
    private static final MapCodec<ChronoFarmerBlock> CODEC =
            simpleCodec(properties -> new ChronoFarmerBlock(properties, 1));

    public ChronoFarmerBlock(Properties properties, int tier) {
        super(properties, tier, new Vector3f(0.35F, 0.95F, 0.7F));
    }

    @Override
    protected MapCodec<ChronoFarmerBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChronoFarmerBlockEntity(pos, state);
    }
}
