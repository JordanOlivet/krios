package io.lakio.krios.core;

import io.lakio.krios.core.koios.KoiosCommand;
import io.lakio.krios.core.platform.NeoForgeRegistrar;
import io.lakio.krios.core.tick.TickManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(Constants.MOD_ID)
public class KriosCore {

    public KriosCore(IEventBus eventBus) {
        // init() fills the deferred registers; attach() hands them to the bus
        // before registry events fire.
        CommonClass.init();
        NeoForgeRegistrar.attach(eventBus, Constants.MOD_ID);
        // Commands ride the game bus, not the mod bus.
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                KoiosCommand.register(event.getDispatcher()));
        // ADR-027: the device scheduler rides the level tick, for every Krios mod.
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                TickManager.tickLevel(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> TickManager.clear());
    }
}
