package io.lakio.krios.industry.platform.services;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Item exchange with PASSIVE neighbors (chests, third-party machines)
 * through each loader's standard item API — NeoForge {@code IItemHandler}
 * capability, Fabric Transfer API — the same dual-adapter pattern as
 * energy. Both loaders cover vanilla containers through their own
 * fallbacks, so common code never special-cases chests. Mod machines are
 * never reached through this service: network routing and the adjacency
 * flow handle them first.
 */
public interface IItemAccess {

    /**
     * Inserts {@code stack} into the container at {@code pos}, approached
     * from {@code face} (the container's side). Returns what did not fit;
     * the input stack is not mutated.
     */
    ItemStack insert(Level level, BlockPos pos, Direction face, ItemStack stack);

    /**
     * Extracts up to {@code max} items of one single kind matching
     * {@code filter} from the container at {@code pos}, approached from
     * {@code face}. Returns the extracted batch (empty = nothing matched).
     */
    ItemStack extract(Level level, BlockPos pos, Direction face,
            Predicate<ItemStack> filter, int max);
}
