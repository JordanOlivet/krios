package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosCoreMenu;
import io.lakio.krios.industry.machine.grinder.FractalGrinderMenu;
import io.lakio.krios.industry.machine.forge.PlasmaForgeMenu;
import io.lakio.krios.industry.machine.reactor.ThermalReactorMenu;
import io.lakio.krios.industry.platform.Services;
import io.lakio.krios.industry.tool.CalibratorMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {

    public static final Supplier<MenuType<ThermalReactorMenu>> THERMAL_REACTOR = register("thermal_reactor",
            () -> Services.REGISTRAR.createMenuType(ThermalReactorMenu::new));

    public static final Supplier<MenuType<io.lakio.krios.industry.machine.photon.PhotonArrayMenu>> PHOTON_ARRAY =
            register("photon_array", () -> Services.REGISTRAR.createMenuType(
                    io.lakio.krios.industry.machine.photon.PhotonArrayMenu::new));

    public static final Supplier<MenuType<FractalGrinderMenu>> FRACTAL_GRINDER = register("fractal_grinder",
            () -> Services.REGISTRAR.createMenuType(FractalGrinderMenu::new));

    public static final Supplier<MenuType<PlasmaForgeMenu>> PLASMA_FORGE = register("plasma_forge",
            () -> Services.REGISTRAR.createMenuType(PlasmaForgeMenu::new));

    public static final Supplier<MenuType<io.lakio.krios.industry.machine.farmer.ChronoFarmerMenu>> CHRONO_FARMER =
            register("chrono_farmer", () -> Services.REGISTRAR.createMenuType(
                    io.lakio.krios.industry.machine.farmer.ChronoFarmerMenu::new));

    public static final Supplier<MenuType<io.lakio.krios.industry.machine.forester.CyclicForesterMenu>> CYCLIC_FORESTER =
            register("cyclic_forester", () -> Services.REGISTRAR.createMenuType(
                    io.lakio.krios.industry.machine.forester.CyclicForesterMenu::new));

    public static final Supplier<MenuType<io.lakio.krios.industry.machine.lure.QuantumLureMenu>> QUANTUM_LURE =
            register("quantum_lure", () -> Services.REGISTRAR.createMenuType(
                    io.lakio.krios.industry.machine.lure.QuantumLureMenu::new));

    public static final Supplier<MenuType<io.lakio.krios.industry.machine.pod.BreedingPodMenu>> BREEDING_POD =
            register("breeding_pod", () -> Services.REGISTRAR.createMenuType(
                    io.lakio.krios.industry.machine.pod.BreedingPodMenu::new));

    public static final Supplier<MenuType<io.lakio.krios.industry.machine.pod.HarvestPodMenu>> HARVEST_POD =
            register("harvest_pod", () -> Services.REGISTRAR.createMenuType(
                    io.lakio.krios.industry.machine.pod.HarvestPodMenu::new));

    public static final Supplier<MenuType<KoiosCoreMenu>> KOIOS_CORE = register("koios_core",
            () -> Services.REGISTRAR.createMenuType(KoiosCoreMenu::new));

    public static final Supplier<MenuType<CalibratorMenu>> CALIBRATOR = register("calibrator",
            () -> Services.REGISTRAR.createMenuType(CalibratorMenu::new));

    private ModMenus() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    // The registry holds MenuType<?>; the cast back to the concrete generic is
    // safe because we registered that exact instance.
    @SuppressWarnings("unchecked")
    private static <T extends AbstractContainerMenu> Supplier<MenuType<T>> register(String name, Supplier<MenuType<T>> factory) {
        Supplier<MenuType<?>> registered =
                Services.REGISTRAR.register(BuiltInRegistries.MENU, Constants.id(name), () -> factory.get());
        return () -> (MenuType<T>) registered.get();
    }
}
