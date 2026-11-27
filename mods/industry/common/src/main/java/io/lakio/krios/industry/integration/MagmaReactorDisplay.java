package io.lakio.krios.industry.integration;

import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.magma.LavaTank;
import io.lakio.krios.industry.machine.magma.MagmaReactorBlockEntity;
import net.minecraft.network.chat.Component;

/**
 * The one "recipe" both viewers show for the Magma Reactor: a bucket of lava
 * into energy (design 2026-11-27). Numbers come from the machine itself.
 */
public record MagmaReactorDisplay(int energyPerBucket, int ratePerTickAtTier2) {

    public static final MagmaReactorDisplay LAVA = new MagmaReactorDisplay(
            MagmaReactorBlockEntity.ENERGY_PER_MB * LavaTank.BUCKET,
            MagmaReactorBlockEntity.GENERATION_PER_TICK * MachineTiers.speedFactor(2));

    public Component rate() {
        return Component.translatable("gui.krios_industry.viewer.magma_reactor.rate", ratePerTickAtTier2);
    }

    public static Component modules() {
        return Component.translatable("gui.krios_industry.viewer.magma_reactor.modules");
    }
}
