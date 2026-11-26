package io.lakio.krios.industry.machine.grinder;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which item a recipe result written as a tag gives (ADR-037). A pack may
 * hold several items under one material tag (two mods with a tin dust): the
 * choice has to be the same on every run and every side.
 */
public final class TagOutputs {

    /**
     * Krios items first, then vanilla, then every other mod by namespace;
     * by path inside one namespace.
     */
    public static final Comparator<ResourceLocation> PREFERENCE = Comparator
            .comparingInt(TagOutputs::rank)
            .thenComparing(ResourceLocation::getNamespace)
            .thenComparing(ResourceLocation::getPath);

    private TagOutputs() {
    }

    private static int rank(ResourceLocation id) {
        if (id.getNamespace().startsWith("krios_")) {
            return 0;
        }
        return id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? 1 : 2;
    }

    /** The preferred id of a tag's members, none for an empty tag. */
    public static Optional<ResourceLocation> pick(Collection<ResourceLocation> ids) {
        return ids.stream().min(PREFERENCE);
    }

    /**
     * The stack a tag result gives now, empty while the tag has no member
     * (the mod that owns the material is not installed). Reads the bound
     * tags, so never call it while recipes are still being loaded.
     */
    public static ItemStack resolve(TagKey<Item> tag, int count) {
        return BuiltInRegistries.ITEM.getTag(tag)
                .flatMap(set -> pick(set.stream()
                        .map(Holder::value)
                        .map(BuiltInRegistries.ITEM::getKey)
                        .toList()))
                .map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id), count))
                .orElse(ItemStack.EMPTY);
    }
}
