package io.lakio.krios.industry.machine.grinder;

import java.util.List;

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
 *
 * <p>The result may also be a list, read in order (ADR-038): the first entry
 * with an item wins. A modded ore names its dust first and its raw metal
 * second, so it still doubles in a pack that has no dust for that metal.
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

    /** One result entry: an item or a tag. */
    private static final Codec<Either<ItemStack, TagResult>> ENTRY_CODEC =
            Codec.either(ItemStack.STRICT_CODEC, TagResult.CODEC);

    /** One entry, or a non-empty list of them in order of preference. */
    static final Codec<List<Either<ItemStack, TagResult>>> OUTPUTS_CODEC = Codec.either(
            ENTRY_CODEC, ExtraCodecs.nonEmptyList(ENTRY_CODEC.listOf())).xmap(
            either -> either.map(List::of, list -> list),
            list -> list.size() == 1 ? Either.left(list.get(0)) : Either.right(list));

    private final Ingredient ingredient;
    private final List<Either<ItemStack, TagResult>> outputs;
    private final int energy;
    private final int time;
    /** Each tag entry once it resolved to an item; an empty resolution is never kept. */
    private final ItemStack[] resolved;

    public GrindingRecipe(Ingredient ingredient, List<Either<ItemStack, TagResult>> outputs, int energy, int time) {
        this.ingredient = ingredient;
        this.outputs = List.copyOf(outputs);
        this.energy = energy;
        this.time = Math.max(1, time);
        this.resolved = new ItemStack[this.outputs.size()];
        java.util.Arrays.fill(resolved, ItemStack.EMPTY);
    }

    public GrindingRecipe(Ingredient ingredient, Either<ItemStack, TagResult> output, int energy, int time) {
        this(ingredient, List.of(output), energy, time);
    }

    public GrindingRecipe(Ingredient ingredient, ItemStack result, int energy, int time) {
        this(ingredient, Either.left(result), energy, time);
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    /** The result entries as the recipe file wrote them, in order of preference. */
    public List<Either<ItemStack, TagResult>> outputs() {
        return outputs;
    }

    /**
     * One operation's base result: the first entry that has an item, empty
     * while none has. Shared: copy before changing it.
     */
    public ItemStack result() {
        for (int i = 0; i < outputs.size(); i++) {
            int index = i;
            ItemStack stack = outputs.get(i).map(item -> item, tag -> resolve(index, tag));
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack resolve(int index, TagResult tagResult) {
        if (resolved[index].isEmpty()) {
            // Recipes are rebuilt on every reload, so a resolved item never
            // goes stale; an empty answer may only mean the tags are not
            // bound yet, and is asked again. The choice between entries is
            // never kept for the same reason: a dust tag bound late wins
            // over the raw metal as soon as it has an item.
            resolved[index] = TagOutputs.resolve(tagResult.tag(), tagResult.count());
        }
        return resolved[index];
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
                OUTPUTS_CODEC.fieldOf("result").forGetter(GrindingRecipe::outputs),
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
