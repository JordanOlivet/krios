package io.lakio.krios.industry.machine.grinder;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.lakio.krios.industry.registry.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * tiers) scales it at craft time in the machine, never in the recipe.
 *
 * <p>The result may name a tag instead of an item (ADR-037):
 * {@code "result": {"tag": "c:dusts/tin", "count": 2}} gives the preferred
 * member of that tag ({@link TagOutputs}). Such a recipe is written once for
 * a material any mod may bring; while its result tag or its ingredient has no
 * item it is not {@link #available()}: it matches nothing and no recipe
 * viewer lists it.
 */
public class GrindingRecipe implements Recipe<SingleRecipeInput> {

    public static final int DEFAULT_ENERGY = 800;
    public static final int DEFAULT_TIME = 100;

    /** A result written as a tag: its preferred member, {@code count} of it. */
    public record TagResult(TagKey<Item> tag, int count) {

        static final Codec<TagResult> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(TagResult::tag),
                ExtraCodecs.intRange(1, Item.ABSOLUTE_MAX_STACK_SIZE).optionalFieldOf("count", 1)
                        .forGetter(TagResult::count)
        ).apply(instance, TagResult::new));
    }

    private final Ingredient ingredient;
    private final Either<ItemStack, TagResult> output;
    private final int energy;
    private final int time;
    /** A tag result once it resolved to an item; an empty resolution is never kept. */
    private ItemStack resolved = ItemStack.EMPTY;

    public GrindingRecipe(Ingredient ingredient, Either<ItemStack, TagResult> output, int energy, int time) {
        this.ingredient = ingredient;
        this.output = output;
        this.energy = energy;
        this.time = Math.max(1, time);
    }

    public GrindingRecipe(Ingredient ingredient, ItemStack result, int energy, int time) {
        this(ingredient, Either.left(result), energy, time);
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    /** The result as the recipe file wrote it: an item or a tag. */
    public Either<ItemStack, TagResult> output() {
        return output;
    }

    /**
     * One operation's base result, empty while a tag result has no item.
     * Shared: copy before changing it.
     */
    public ItemStack result() {
        return output.map(stack -> stack, this::resolve);
    }

    private ItemStack resolve(TagResult tagResult) {
        if (resolved.isEmpty()) {
            // Recipes are rebuilt on every reload, so a resolved item never
            // goes stale; an empty answer may only mean the tags are not
            // bound yet, and is asked again.
            resolved = TagOutputs.resolve(tagResult.tag(), tagResult.count());
        }
        return resolved;
    }

    /**
     * Whether this recipe can run in this pack: its ingredient names at least
     * one item and its result exists. NeoForge fills an empty tag ingredient
     * with a named barrier, which is no input either.
     */
    public boolean available() {
        if (result().isEmpty()) {
            return false;
        }
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty() && !stack.is(Items.BARRIER)) {
                return true;
            }
        }
        return false;
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
        return ingredient.test(input.item()) && available();
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result();
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
                Codec.either(ItemStack.STRICT_CODEC, TagResult.CODEC).fieldOf("result")
                        .forGetter(GrindingRecipe::output),
                Codec.INT.optionalFieldOf("energy", DEFAULT_ENERGY).forGetter(GrindingRecipe::energy),
                Codec.INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(GrindingRecipe::time)
        ).apply(instance, GrindingRecipe::new));

        /**
         * The server sends the item it resolved, never the tag: a client
         * shows and predicts what the server will give, whatever order its
         * own tags and recipes arrive in. An unavailable recipe travels with
         * an empty result.
         */
        private static final StreamCodec<RegistryFriendlyByteBuf, GrindingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, GrindingRecipe::ingredient,
                ItemStack.OPTIONAL_STREAM_CODEC, GrindingRecipe::result,
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
