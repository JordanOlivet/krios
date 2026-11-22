package io.lakio.krios.core.registry;

import java.util.function.Supplier;

import io.lakio.krios.core.Constants;
import io.lakio.krios.core.koios.KoiosCoreMenu;
import io.lakio.krios.core.platform.Services;
import io.lakio.krios.core.tool.CalibratorMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {

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
