package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.machine.grinder.FractalGrinderBlockEntity;
import io.lakio.krios.industry.machine.forge.PlasmaForgeBlockEntity;
import io.lakio.krios.industry.machine.reactor.ThermalReactorBlockEntity;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    // One BE type per machine LINE: both tier blocks share it (the BE reads
    // its tier from the block), so tickers, menus and platform energy
    // capabilities cover every tier for free.
    public static final Supplier<BlockEntityType<ThermalReactorBlockEntity>> THERMAL_REACTOR = register("thermal_reactor",
            () -> Services.REGISTRAR.createBlockEntityType(ThermalReactorBlockEntity::new,
                    ModBlocks.THERMAL_REACTOR.get(), ModBlocks.THERMAL_REACTOR_TIER2.get()));

    public static final Supplier<BlockEntityType<io.lakio.krios.industry.machine.photon.PhotonArrayBlockEntity>> PHOTON_ARRAY =
            register("photon_array", () -> Services.REGISTRAR.createBlockEntityType(
                    io.lakio.krios.industry.machine.photon.PhotonArrayBlockEntity::new,
                    ModBlocks.PHOTON_ARRAY.get()));

    public static final Supplier<BlockEntityType<FractalGrinderBlockEntity>> FRACTAL_GRINDER = register("fractal_grinder",
            () -> Services.REGISTRAR.createBlockEntityType(FractalGrinderBlockEntity::new,
                    ModBlocks.FRACTAL_GRINDER.get(), ModBlocks.FRACTAL_GRINDER_TIER2.get()));

    public static final Supplier<BlockEntityType<PlasmaForgeBlockEntity>> PLASMA_FORGE = register("plasma_forge",
            () -> Services.REGISTRAR.createBlockEntityType(PlasmaForgeBlockEntity::new,
                    ModBlocks.PLASMA_FORGE.get(), ModBlocks.PLASMA_FORGE_TIER2.get()));

    public static final Supplier<BlockEntityType<io.lakio.krios.industry.machine.farmer.ChronoFarmerBlockEntity>> CHRONO_FARMER =
            register("chrono_farmer", () -> Services.REGISTRAR.createBlockEntityType(
                    io.lakio.krios.industry.machine.farmer.ChronoFarmerBlockEntity::new,
                    ModBlocks.CHRONO_FARMER.get(), ModBlocks.CHRONO_FARMER_TIER2.get()));

    public static final Supplier<BlockEntityType<io.lakio.krios.industry.machine.forester.CyclicForesterBlockEntity>> CYCLIC_FORESTER =
            register("cyclic_forester", () -> Services.REGISTRAR.createBlockEntityType(
                    io.lakio.krios.industry.machine.forester.CyclicForesterBlockEntity::new,
                    ModBlocks.CYCLIC_FORESTER.get(), ModBlocks.CYCLIC_FORESTER_TIER2.get()));

    public static final Supplier<BlockEntityType<io.lakio.krios.industry.machine.lure.QuantumLureBlockEntity>> QUANTUM_LURE =
            register("quantum_lure", () -> Services.REGISTRAR.createBlockEntityType(
                    io.lakio.krios.industry.machine.lure.QuantumLureBlockEntity::new,
                    ModBlocks.QUANTUM_LURE.get(), ModBlocks.QUANTUM_LURE_TIER2.get()));

    public static final Supplier<BlockEntityType<io.lakio.krios.industry.machine.pod.BreedingPodBlockEntity>> BREEDING_POD =
            register("breeding_pod", () -> Services.REGISTRAR.createBlockEntityType(
                    io.lakio.krios.industry.machine.pod.BreedingPodBlockEntity::new,
                    ModBlocks.BREEDING_POD.get(), ModBlocks.BREEDING_POD_TIER2.get()));

    public static final Supplier<BlockEntityType<io.lakio.krios.industry.machine.pod.HarvestPodBlockEntity>> HARVEST_POD =
            register("harvest_pod", () -> Services.REGISTRAR.createBlockEntityType(
                    io.lakio.krios.industry.machine.pod.HarvestPodBlockEntity::new,
                    ModBlocks.HARVEST_POD.get(), ModBlocks.HARVEST_POD_TIER2.get()));

    public static final Supplier<BlockEntityType<io.lakio.krios.industry.machine.field.PhaseDeathFieldBlockEntity>> PHASE_DEATH_FIELD =
            register("phase_death_field", () -> Services.REGISTRAR.createBlockEntityType(
                    io.lakio.krios.industry.machine.field.PhaseDeathFieldBlockEntity::new,
                    ModBlocks.PHASE_DEATH_FIELD.get(), ModBlocks.PHASE_DEATH_FIELD_TIER2.get()));

    public static final Supplier<BlockEntityType<io.lakio.krios.industry.machine.synth.BioSynthesizerBlockEntity>> BIO_SYNTHESIZER =
            register("bio_synthesizer", () -> Services.REGISTRAR.createBlockEntityType(
                    io.lakio.krios.industry.machine.synth.BioSynthesizerBlockEntity::new,
                    ModBlocks.BIO_SYNTHESIZER.get(), ModBlocks.BIO_SYNTHESIZER_TIER2.get()));

    public static final Supplier<BlockEntityType<KoiosCoreBlockEntity>> KOIOS_CORE = register("koios_core",
            () -> Services.REGISTRAR.createBlockEntityType(KoiosCoreBlockEntity::new, ModBlocks.KOIOS_CORE.get()));

    private ModBlockEntities() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    // The registry holds BlockEntityType<?>; the cast back to the concrete
    // generic is safe because we registered that exact instance.
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> Supplier<BlockEntityType<T>> register(String name, Supplier<BlockEntityType<T>> factory) {
        Supplier<BlockEntityType<?>> registered =
                Services.REGISTRAR.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Constants.id(name), () -> factory.get());
        return () -> (BlockEntityType<T>) registered.get();
    }
}
