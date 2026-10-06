package io.lakio.krios.industry.machine.grinder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.lakio.krios.industry.registry.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Datapack-driven grinding recipe (ADR-007).
 * JSON: { "type": "krios_industry:grinding", "ingredient": {...},
 *         "result": {"id": ..., "count": n}, "energy": 800, "time": 100 }
 * The result count is the base yield; the evolving multiplier (yield modules,
 * post-MVP) scales it at craft time in the machine, never in the recipe.
 */
public class GrindingRecipe implements Recipe<SingleRecipeInput> {

    public static final int DEFAULT_ENERGY = 800;
    public static final int DEFAULT_TIME = 100;

    private final Ingredient ingredient;
    private final ItemStack result;
    private final int energy;
    private final int time;

    public GrindingRecipe(Ingredient ingredient, ItemStack result, int energy, int time) {
        this.ingredient = ingredient;
        this.result = result;
        this.energy = energy;
        this.time = Math.max(1, time);
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public ItemStack result() {
        return result;
    }

    /** Total energy for one operation. */
    public int energy() {
        return energy;
    }

    /** Operation duration in ticks. */
    public int time() {
        return time;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.GRINDING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.GRINDING.get();
    }

    public static class Serializer implements RecipeSerializer<GrindingRecipe> {

        private static final MapCodec<GrindingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(GrindingRecipe::ingredient),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(GrindingRecipe::result),
                Codec.INT.optionalFieldOf("energy", DEFAULT_ENERGY).forGetter(GrindingRecipe::energy),
                Codec.INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(GrindingRecipe::time)
        ).apply(instance, GrindingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, GrindingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, GrindingRecipe::ingredient,
                ItemStack.STREAM_CODEC, GrindingRecipe::result,
                ByteBufCodecs.VAR_INT, GrindingRecipe::energy,
                ByteBufCodecs.VAR_INT, GrindingRecipe::time,
                GrindingRecipe::new);

        @Override
        public MapCodec<GrindingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, GrindingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
