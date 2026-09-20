package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.generator.GeneratorMenu;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {

    public static final Supplier<MenuType<GeneratorMenu>> GENERATOR = register("generator",
            () -> Services.REGISTRAR.createMenuType(GeneratorMenu::new));

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
