package io.lakio.krios.industry.mixin;

import net.minecraft.world.entity.animal.armadillo.Armadillo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The armadillo's scute clock is private (ADR-023): the Harvest Pod's
 * Speed modules move it forward like the chicken's public egg clock.
 */
@Mixin(Armadillo.class)
public interface ArmadilloAccessor {

    @Accessor("scuteTime")
    int krios$getScuteTime();

    @Accessor("scuteTime")
    void krios$setScuteTime(int ticks);
}
