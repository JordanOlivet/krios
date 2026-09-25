package io.lakio.krios.industry;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.koios.KoiosCommand;
import io.lakio.krios.industry.machine.generator.GeneratorBlockEntity;
import io.lakio.krios.industry.platform.NeoForgeRegistrar;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(Constants.MOD_ID)
public class KriosIndustry {

    public KriosIndustry(IEventBus eventBus) {
        // init() fills the deferred registers; attach() hands them to the bus
        // before registry events fire.
        CommonClass.init();
        NeoForgeRegistrar.attach(eventBus);
        eventBus.addListener(KriosIndustry::registerCapabilities);
        // Commands ride the game bus, not the mod bus.
        NeoForge.EVENT_BUS.addListener(KriosIndustry::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        KoiosCommand.register(event.getDispatcher());
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.GENERATOR.get(),
                (generator, side) -> new ExtractOnlyEnergyStorage(generator.getEnergy()));
    }

    /** FE view of the internal buffer: neighbors may pull, never push. */
    private record ExtractOnlyEnergyStorage(EnergyBuffer buffer) implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (simulate) {
                return Math.min(Math.min(maxExtract, buffer.getMaxExtract()), buffer.getStored());
            }
            return buffer.extract(maxExtract);
        }

        @Override
        public int getEnergyStored() {
            return buffer.getStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return buffer.getCapacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
