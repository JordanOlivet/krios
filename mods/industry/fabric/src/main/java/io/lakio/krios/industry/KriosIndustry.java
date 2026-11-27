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
        // internal buffer directly. A configured face gates third-party TRE
        // access (complete contract); virgin faces stay exposed.
        EnergyStorage.SIDED.registerForBlockEntity(
                (reactor, direction) -> direction == null || reactor.allowsEnergyExtract(direction)
                        ? new ExtractOnlyEnergyAdapter(reactor.getEnergy(), reactor::setChanged) : null,
                ModBlockEntities.THERMAL_REACTOR.get());
        EnergyStorage.SIDED.registerForBlockEntity(
                (reactor, direction) -> direction == null || reactor.allowsEnergyExtract(direction)
                        ? new ExtractOnlyEnergyAdapter(reactor.getEnergy(), reactor::setChanged) : null,
                ModBlockEntities.MAGMA_REACTOR.get());
        // Lava from other mods' pipes (ADR-039): in through virgin and FLUID-in
        // faces, out only through manual FLUID-out faces; a null side sees the
        // tank, read-only.
        net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.registerForBlockEntity(
                io.lakio.krios.industry.fluid.LavaTankStorage::new,
                ModBlockEntities.MAGMA_REACTOR.get());
        // Photon array: no face config (v1 scope), every side is extractable.
        EnergyStorage.SIDED.registerForBlockEntity(
                (array, direction) -> new ExtractOnlyEnergyAdapter(array.getEnergy(), array::setChanged),
                ModBlockEntities.PHOTON_ARRAY.get());
    }
}
