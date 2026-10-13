package io.lakio.krios.industry.mixin;

import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.entity.animal.MushroomCow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A fed brown mooshroom's stew effects are private (ADR-023): the Harvest
 * Pod milks a suspicious stew from it exactly as a player's bowl would.
 */
@Mixin(MushroomCow.class)
public interface MushroomCowAccessor {

    @Accessor("stewEffects")
    SuspiciousStewEffects krios$getStewEffects();

    @Accessor("stewEffects")
    void krios$setStewEffects(SuspiciousStewEffects effects);
}
