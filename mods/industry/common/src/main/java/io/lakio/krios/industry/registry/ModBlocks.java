package io.lakio.krios.industry.registry;

import io.lakio.krios.core.platform.Services;
import java.util.function.Function;
import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.grinder.FractalGrinderBlock;
import io.lakio.krios.industry.machine.forge.PlasmaForgeBlock;
import io.lakio.krios.industry.machine.reactor.ThermalReactorBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {

    public static final Supplier<Block> THERMAL_REACTOR = registerWithItem("thermal_reactor",
            () -> new ThermalReactorBlock(machineProperties(12), 1));

    public static final Supplier<Block> THERMAL_REACTOR_TIER2 = registerWithItem("thermal_reactor_tier2",
            () -> new ThermalReactorBlock(machineProperties(12), 2));

    public static final Supplier<Block> PHOTON_ARRAY = registerWithItem("photon_array",
            // Full cube (machine body + cell deck on top): standard occlusion.
            () -> new io.lakio.krios.industry.machine.photon.PhotonArrayBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .requiresCorrectToolForDrops()
                            .strength(3.0F, 4.0F)
                            .sound(SoundType.METAL)));

    public static final Supplier<Block> FRACTAL_GRINDER = registerWithItem("fractal_grinder",
            () -> new FractalGrinderBlock(machineProperties(9), 1));

    public static final Supplier<Block> FRACTAL_GRINDER_TIER2 = registerWithItem("fractal_grinder_tier2",
            () -> new FractalGrinderBlock(machineProperties(9), 2));

    public static final Supplier<Block> PLASMA_FORGE = registerWithItem("plasma_forge",
            () -> new PlasmaForgeBlock(machineProperties(11), 1));

    public static final Supplier<Block> PLASMA_FORGE_TIER2 = registerWithItem("plasma_forge_tier2",
            () -> new PlasmaForgeBlock(machineProperties(11), 2));

    public static final Supplier<Block> CHRONO_FARMER = registerWithItem("chrono_farmer",
            () -> new io.lakio.krios.industry.machine.farmer.ChronoFarmerBlock(machineProperties(9), 1));

    public static final Supplier<Block> CHRONO_FARMER_TIER2 = registerWithItem("chrono_farmer_tier2",
            () -> new io.lakio.krios.industry.machine.farmer.ChronoFarmerBlock(machineProperties(9), 2));

    public static final Supplier<Block> CYCLIC_FORESTER = registerWithItem("cyclic_forester",
            () -> new io.lakio.krios.industry.machine.forester.CyclicForesterBlock(machineProperties(9), 1));

    public static final Supplier<Block> CYCLIC_FORESTER_TIER2 = registerWithItem("cyclic_forester_tier2",
            () -> new io.lakio.krios.industry.machine.forester.CyclicForesterBlock(machineProperties(9), 2));

    public static final Supplier<Block> QUANTUM_LURE = registerWithItem("quantum_lure",
            () -> new io.lakio.krios.industry.machine.lure.QuantumLureBlock(machineProperties(9), 1));

    public static final Supplier<Block> QUANTUM_LURE_TIER2 = registerWithItem("quantum_lure_tier2",
            () -> new io.lakio.krios.industry.machine.lure.QuantumLureBlock(machineProperties(9), 2));

    public static final Supplier<Block> BREEDING_POD = registerWithItem("breeding_pod",
            () -> new io.lakio.krios.industry.machine.pod.BreedingPodBlock(machineProperties(9), 1));

    public static final Supplier<Block> BREEDING_POD_TIER2 = registerWithItem("breeding_pod_tier2",
            () -> new io.lakio.krios.industry.machine.pod.BreedingPodBlock(machineProperties(9), 2));

    public static final Supplier<Block> HARVEST_POD = registerWithItem("harvest_pod",
            () -> new io.lakio.krios.industry.machine.pod.HarvestPodBlock(machineProperties(9), 1));

    public static final Supplier<Block> HARVEST_POD_TIER2 = registerWithItem("harvest_pod_tier2",
            () -> new io.lakio.krios.industry.machine.pod.HarvestPodBlock(machineProperties(9), 2));

    public static final Supplier<Block> PHASE_DEATH_FIELD = registerWithItem("phase_death_field",
            () -> new io.lakio.krios.industry.machine.field.PhaseDeathFieldBlock(machineProperties(9), 1));

    public static final Supplier<Block> PHASE_DEATH_FIELD_TIER2 = registerWithItem("phase_death_field_tier2",
            () -> new io.lakio.krios.industry.machine.field.PhaseDeathFieldBlock(machineProperties(9), 2));

    public static final Supplier<Block> BIO_SYNTHESIZER = registerWithItem("bio_synthesizer",
            () -> new io.lakio.krios.industry.machine.synth.BioSynthesizerBlock(machineProperties(9), 1));

    public static final Supplier<Block> BIO_SYNTHESIZER_TIER2 = registerWithItem("bio_synthesizer_tier2",
            () -> new io.lakio.krios.industry.machine.synth.BioSynthesizerBlock(machineProperties(9), 2));

    /**
     * Shared machine block properties: sculpted models (not full cubes) and
     * a real glow while LIT. Both tiers of a machine share the same shell.
     */
    private static BlockBehaviour.Properties machineProperties(int litLight) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops()
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .lightLevel(state -> state.getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? litLight : 0);
    }

    private ModBlocks() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    private static Supplier<Block> registerWithItem(String name, Supplier<Block> factory) {
        return registerWithItem(name, factory, block -> new BlockItem(block, new Item.Properties()));
    }

    private static Supplier<Block> registerWithItem(String name, Supplier<Block> factory,
            Function<Block, BlockItem> itemFactory) {
        Supplier<Block> block = Services.REGISTRAR.register(BuiltInRegistries.BLOCK, Constants.id(name), factory);
        Services.REGISTRAR.register(BuiltInRegistries.ITEM, Constants.id(name),
                () -> itemFactory.apply(block.get()));
        return block;
    }
}
