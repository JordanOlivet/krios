package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.machine.crusher.CrusherBlockEntity;
import io.lakio.krios.industry.machine.generator.GeneratorBlockEntity;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    public static final Supplier<BlockEntityType<GeneratorBlockEntity>> GENERATOR = register("generator",
            () -> Services.REGISTRAR.createBlockEntityType(GeneratorBlockEntity::new, ModBlocks.GENERATOR.get()));

    public static final Supplier<BlockEntityType<CrusherBlockEntity>> CRUSHER = register("crusher",
            () -> Services.REGISTRAR.createBlockEntityType(CrusherBlockEntity::new, ModBlocks.CRUSHER.get()));

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
