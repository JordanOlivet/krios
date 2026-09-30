package io.lakio.krios.industry.koios;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The material half of a critical breakthrough (design 2026-09-30): with
 * the core's XP sitting at the tier cap, right-clicking the formed
 * structure with a chip consumes it and pays the tier-up. Crafted from
 * bulk previous-tier outputs only (anti chicken-and-egg rule).
 */
public class ComputeChipItem extends Item {

    public ComputeChipItem(Properties properties) {
        super(properties);
    }

    /**
     * The ritual gesture, shared by the core and casing blocks (the formed
     * structure acts as one block). Swallows the click either way so a
     * failed attempt explains itself instead of opening the terminal.
     */
    public static ItemInteractionResult useOnStructure(ItemStack stack, Level level, BlockPos pos,
            Player player) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        KoiosCoreBlockEntity core = level.getBlockEntity(pos) instanceof KoiosCoreBlockEntity self
                && self.isFormed() ? self : NeuralCasingBlock.findFormedCore(level, pos);
        if (core == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (core.tryBreakthrough()) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.translatable(
                    "gui.krios_industry.koios.breakthrough", core.getTier()), true);
        } else if (core.isCriticalPending()) {
            // Threshold reached but the next tier's content is not out yet.
            player.displayClientMessage(Component.translatable(
                    "gui.krios_industry.koios.breakthrough_locked", core.getTier() + 1), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "gui.krios_industry.koios.breakthrough_not_ready"), true);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
