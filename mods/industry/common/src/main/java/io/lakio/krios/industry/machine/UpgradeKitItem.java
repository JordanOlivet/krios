package io.lakio.krios.industry.machine;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.lakio.krios.industry.network.FlowPorts;
import io.lakio.krios.industry.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * In-situ machine upgrade (design 2026-09-30): right-click a machine of
 * the previous tier, the kit is consumed and the block becomes its
 * next-tier variant — inventory, modules, energy and face config all
 * survive (the only path that preserves face config, since break and
 * re-place is factory-new by design). The kit carries the whole upgrade
 * cost; the direct tier N machine recipe is just machine + kit.
 */
public class UpgradeKitItem extends Item {

    private final int targetTier;

    /** Tier ladder per machine line; lazily resolved (registration order). */
    private static final Map<Supplier<Block>, Supplier<Block>> TIER2_TARGETS = Map.of(
            ModBlocks.THERMAL_REACTOR, ModBlocks.THERMAL_REACTOR_TIER2,
            ModBlocks.FRACTAL_GRINDER, ModBlocks.FRACTAL_GRINDER_TIER2,
            ModBlocks.PLASMA_FORGE, ModBlocks.PLASMA_FORGE_TIER2,
            ModBlocks.CHRONO_FARMER, ModBlocks.CHRONO_FARMER_TIER2,
            ModBlocks.CYCLIC_FORESTER, ModBlocks.CYCLIC_FORESTER_TIER2,
            ModBlocks.QUANTUM_LURE, ModBlocks.QUANTUM_LURE_TIER2,
            ModBlocks.BREEDING_POD, ModBlocks.BREEDING_POD_TIER2);

    public UpgradeKitItem(int targetTier, Properties properties) {
        super(properties);
        this.targetTier = targetTier;
    }

    /** Called from the machine blocks' useItemOn (before the menu opens). */
    public ItemInteractionResult useOnMachine(ItemStack stack, Level level, BlockPos pos,
            Player player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TieredMachine machine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (machine.machineTier() != targetTier - 1) {
            player.displayClientMessage(Component.translatable(
                    "gui.krios_industry.upgrade_kit.wrong_tier", targetTier, targetTier - 1), true);
            return ItemInteractionResult.sidedSuccess(false);
        }
        Block target = targetFor(state.getBlock());
        if (target == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Swap the block in place, carrying the whole block-entity state
        // over. Clearing first keeps onRemove from dropping the inventory
        // we just saved (both tiers share one BE type, so the tag fits).
        var saved = blockEntity.saveWithoutMetadata(level.registryAccess());
        if (blockEntity instanceof Clearable clearable) {
            clearable.clearContent();
        }
        BlockState upgraded = target.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (upgraded.hasProperty(property)) {
                upgraded = copyProperty(upgraded, state, property);
            }
        }
        level.setBlock(pos, upgraded, 3);
        BlockEntity fresh = level.getBlockEntity(pos);
        if (fresh != null) {
            fresh.loadWithComponents(saved, level.registryAccess());
            fresh.setChanged();
            // The swap dropped any pending scheduled tick (vanilla keys them
            // by block), so the ADR-017 wake chain must be re-entered here —
            // the carried-over manual item faces otherwise go unserviced
            // until a player touches the face config again.
            if (fresh instanceof FlowPorts ports) {
                ItemFlow.scheduleWake(level, pos, ports.faceConfig());
            }
        }
        level.sendBlockUpdated(pos, upgraded, upgraded, 3);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.4F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable(
                "gui.krios_industry.upgrade_kit.done", targetTier), true);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Nullable
    private Block targetFor(Block current) {
        if (targetTier == 2) {
            for (Map.Entry<Supplier<Block>, Supplier<Block>> entry : TIER2_TARGETS.entrySet()) {
                if (entry.getKey().get() == current) {
                    return entry.getValue().get();
                }
            }
        }
        return null;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState to,
            BlockState from, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".desc", targetTier)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
