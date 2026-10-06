package io.lakio.krios.industry;

import io.lakio.krios.industry.energy.ExtractOnlyEnergyAdapter;
import io.lakio.krios.industry.koios.KoiosCommand;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
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
        // Photon array: no face config (v1 scope), every side is extractable.
        EnergyStorage.SIDED.registerForBlockEntity(
                (array, direction) -> new ExtractOnlyEnergyAdapter(array.getEnergy(), array::setChanged),
                ModBlockEntities.PHOTON_ARRAY.get());
        // Ore veins: features/placements are datapack JSONs in common; only
        // the biome injection is loader code (NeoForge does it via a
        // biome_modifier JSON instead).
        addOre("chronite_ore");
        addOre("neurite_ore");
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> KoiosCommand.register(dispatcher));
    }

    private static void addOre(String name) {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ResourceKey.create(Registries.PLACED_FEATURE, Constants.id(name)));
    }
}
