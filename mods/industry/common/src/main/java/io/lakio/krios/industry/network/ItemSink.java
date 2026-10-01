package io.lakio.krios.industry.network;

import net.minecraft.world.item.ItemStack;

/**
 * A node that can take items from the network's batch routing. Engine
 * contract only (ADR-013): how an implementation maps the offer onto its
 * inventory — face contracts, slot discipline, column election — is its
 * own business.
 */
public interface ItemSink {

    /**
     * Take up to {@code max} items of {@code stack} (a live template —
     * implementations must copy, never mutate it) and return the count
     * actually accepted.
     */
    int insertNetworkItems(ItemStack stack, int max);
}
