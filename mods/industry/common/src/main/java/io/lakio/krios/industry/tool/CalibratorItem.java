package io.lakio.krios.industry.tool;

import java.util.List;

import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.koios.NeuralCasingBlock;
import io.lakio.krios.industry.network.NetworkConductor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The ecosystem's configuration tool. First duty: sneak + right-click on a
 * formed KOIOS shell face selects that face as the AI's screen. Later duties
 * (machine rotation, side configuration, careful dismantling) hook in here.
 */
public class CalibratorItem extends Item {

    public CalibratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!context.isSecondaryUseActive()) {
            // Plain right-click on any network block: live diagnostic UI.
            if (level.getBlockState(pos).getBlock() instanceof NetworkConductor) {
                Player user = context.getPlayer();
                if (!level.isClientSide && user != null) {
                    user.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inventory, p) -> new CalibratorMenu(id, inventory, level, pos),
                            Component.translatable("gui.krios_industry.calibrator.diagnostic")));
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof NeuralCasingBlock)) {
            // Sneak + click any other block: toggle the construction
            // blueprint on top of it. Pure visual aid — client-side only.
            return handleBlueprint(context);
        }
        KoiosCoreBlockEntity core = NeuralCasingBlock.findFormedCore(level, pos);
        if (core == null) {
            return handleBlueprint(context);
        }
        Direction face = context.getClickedFace();
        // The clicked casing must sit on that side of the structure, so the
        // whole multiblock behaves as one big block whichever casing is hit.
        BlockPos offset = pos.subtract(core.getBlockPos());
        if (offset.get(face.getAxis()) != face.getAxisDirection().getStep()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            core.setScreenSide(face);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
            Player player = context.getPlayer();
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        "gui.krios_industry.calibrator.screen_set",
                        Component.translatable("gui.krios_industry.direction." + face.getName())), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Client-side blueprint anchor toggle (no server state to keep). */
    private static InteractionResult handleBlueprint(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            // Single definition today; a picker arrives with the second
            // multiblock (Blueprint.ALL is the menu).
            Blueprint blueprint = Blueprint.KOIOS_CORE;
            boolean placed = io.lakio.krios.industry.client.BlueprintState
                    .toggle(context.getClickedPos(), blueprint);
            Player player = context.getPlayer();
            if (player != null) {
                player.displayClientMessage(placed
                        ? Component.translatable("gui.krios_industry.calibrator.blueprint_set",
                                Component.translatable(blueprint.nameKey()))
                        : Component.translatable("gui.krios_industry.calibrator.blueprint_cleared"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".blueprint").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(getDescriptionId() + ".overlay").withStyle(ChatFormatting.DARK_AQUA));
    }
}
