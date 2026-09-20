package io.lakio.krios.industry;

import io.lakio.krios.industry.platform.NeoForgeRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class KriosIndustry {

    public KriosIndustry(IEventBus eventBus) {
        // init() fills the deferred registers; attach() hands them to the bus
        // before registry events fire.
        CommonClass.init();
        NeoForgeRegistrar.attach(eventBus);
    }
}
