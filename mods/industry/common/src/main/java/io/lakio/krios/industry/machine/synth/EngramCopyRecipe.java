package io.lakio.krios.industry.machine.synth;

import io.lakio.krios.industry.registry.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Engram duplication (user, 2026-10-13): one imprinted Engram plus N
 * blanks in the grid give N + 1 imprinted copies — the signature is data.
 */
public class EngramCopyRecipe extends CustomRecipe {

    public EngramCopyRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input, level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack source = ItemStack.EMPTY;
        int blanks = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!(stack.getItem() instanceof EngramItem)) {
                return ItemStack.EMPTY;
            }
            if (EngramItem.isImprinted(stack)) {
                if (!source.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                source = stack;
            } else {
                blanks++;
            }
        }
        return source.isEmpty() || blanks == 0 ? ItemStack.EMPTY : source.copyWithCount(blanks + 1);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.ENGRAM_COPY_SERIALIZER.get();
    }
}
