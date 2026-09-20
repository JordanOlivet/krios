package io.lakio.krios.industry;

import io.lakio.krios.industry.energy.ExtractOnlyEnergyAdapter;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.fabricmc.api.ModInitializer;
import team.reborn.energy.api.EnergyStorage;

public class KriosIndustry implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();
        // Adapter allocated per external query only; our own machines use the
        // internal buffer directly.
        EnergyStorage.SIDED.registerForBlockEntity(
                (generator, direction) -> new ExtractOnlyEnergyAdapter(generator),
                ModBlockEntities.GENERATOR.get());
    }
}
