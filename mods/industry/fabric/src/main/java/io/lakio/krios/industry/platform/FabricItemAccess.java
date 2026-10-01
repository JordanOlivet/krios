package io.lakio.krios.industry.platform;

import java.util.function.Predicate;

import io.lakio.krios.industry.platform.services.IItemAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Passive-container item exchange through the Fabric Transfer API
 * (vanilla containers included — Fabric's sided fallback covers them).
 */
public class FabricItemAccess implements IItemAccess {

    @Override
    public ItemStack insert(Level level, BlockPos pos, Direction face, ItemStack stack) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, face);
        if (storage == null || !storage.supportsInsertion()) {
            return stack;
        }
        long inserted;
        try (Transaction tx = Transaction.openOuter()) {
            inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), tx);
            tx.commit();
        }
        return inserted <= 0 ? stack
                : stack.copyWithCount(stack.getCount() - (int) inserted);
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, Direction face,
            Predicate<ItemStack> filter, int max) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, face);
        if (storage == null || !storage.supportsExtraction()) {
            return ItemStack.EMPTY;
        }
        // One single item kind per batch: the first matching view decides,
        // later views only top the batch up.
        try (Transaction tx = Transaction.openOuter()) {
            ItemVariant chosen = null;
            long taken = 0;
            for (StorageView<ItemVariant> view : storage) {
                if (view.isResourceBlank()) {
                    continue;
                }
                ItemVariant variant = view.getResource();
                if (chosen == null) {
                    if (!filter.test(variant.toStack())) {
                        continue;
                    }
                } else if (!variant.equals(chosen)) {
                    continue;
                }
                long room = Math.min(max, variant.getItem().getDefaultMaxStackSize()) - taken;
                if (room <= 0) {
                    break;
                }
                long got = view.extract(variant, room, tx);
                if (got > 0) {
                    chosen = variant;
                    taken += got;
                }
            }
            if (chosen == null || taken == 0) {
                return ItemStack.EMPTY;
            }
            tx.commit();
            return chosen.toStack((int) taken);
        }
    }
}
