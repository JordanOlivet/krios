package io.lakio.krios.core;

import io.lakio.krios.core.koios.KoiosCommand;
import io.lakio.krios.core.tick.TickManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;

public class KriosCore implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();
        // Ore veins: features/placements are datapack JSONs in common; only
        // the biome injection is loader code (NeoForge does it via a
        // biome_modifier JSON instead).
        addOre("chronite_ore");
        addOre("neurite_ore");
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> KoiosCommand.register(dispatcher));
        // ADR-027: the device scheduler rides the level tick, for every Krios mod.
        ServerTickEvents.END_WORLD_TICK.register(TickManager::tickLevel);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TickManager.clear());
    }

    private static void addOre(String name) {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ResourceKey.create(Registries.PLACED_FEATURE, Constants.id(name)));
    }
}
