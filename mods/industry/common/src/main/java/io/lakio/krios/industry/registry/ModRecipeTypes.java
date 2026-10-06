package io.lakio.krios.industry.registry;

import java.util.function.Supplier;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.machine.grinder.GrindingRecipe;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

public final class ModRecipeTypes {

    public static final Supplier<RecipeType<GrindingRecipe>> GRINDING = registerType("grinding");

    public static final Supplier<RecipeSerializer<GrindingRecipe>> GRINDING_SERIALIZER =
            registerSerializer("grinding", GrindingRecipe.Serializer::new);

    private ModRecipeTypes() {
    }

    public static void init() {
        // Forces static registration; called once from CommonClass.init().
    }

    // Both registries hold wildcard generics; casts are safe because we hand
    // back the exact registered instances.
    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> Supplier<RecipeType<T>> registerType(String name) {
        Supplier<RecipeType<?>> registered = Services.REGISTRAR.register(
                BuiltInRegistries.RECIPE_TYPE, Constants.id(name), () -> new RecipeType<T>() {
                    @Override
                    public String toString() {
                        return Constants.id(name).toString();
                    }
                });
        return () -> (RecipeType<T>) registered.get();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> Supplier<RecipeSerializer<T>> registerSerializer(
            String name, Supplier<RecipeSerializer<T>> factory) {
        Supplier<RecipeSerializer<?>> registered = Services.REGISTRAR.register(
                BuiltInRegistries.RECIPE_SERIALIZER, Constants.id(name), () -> factory.get());
        return () -> (RecipeSerializer<T>) registered.get();
    }
}
