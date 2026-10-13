package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosData;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class ModDataComponents {

    public static final Supplier<DataComponentType<KoiosData>> KOIOS_DATA = register("koios_data",
            () -> DataComponentType.<KoiosData>builder().persistent(KoiosData.CODEC).build());

    /** An Engram's imprinted species (entity type id); synced for names and tooltips. */
    public static final Supplier<DataComponentType<ResourceLocation>> ENGRAM = register("engram",
            () -> DataComponentType.<ResourceLocation>builder().persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC).build());

    private ModDataComponents() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    // The registry holds DataComponentType<?>; the cast back to the concrete
    // generic is safe because we registered that exact instance.
    @SuppressWarnings("unchecked")
    private static <T> Supplier<DataComponentType<T>> register(String name, Supplier<DataComponentType<T>> factory) {
        Supplier<DataComponentType<?>> registered =
                Services.REGISTRAR.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Constants.id(name), () -> factory.get());
        return () -> (DataComponentType<T>) registered.get();
    }
}
