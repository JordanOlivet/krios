package io.lakio.krios.industry.mixin;

import io.lakio.krios.industry.machine.HydrationZones;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Farmland inside a Chrono Farmer zone counts as near water (ADR-023): no
 * loader API covers it on Fabric, and one common hook beats two divergent
 * paths (NeoForge's FarmlandWaterManager tickets are not persisted either).
 */
@Mixin(FarmBlock.class)
public abstract class FarmBlockMixin {

    @Inject(method = "isNearWater", at = @At("HEAD"), cancellable = true)
    private static void krios$hydrationZones(LevelReader level, BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (HydrationZones.isHydrated(level, pos)) {
            cir.setReturnValue(true);
        }
    }
}
