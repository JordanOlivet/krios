package io.lakio.krios.industry.machine.synth;

import java.util.List;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * The Engram (design 2026-10-13): KOIOS's captured signature of a
 * creature. A blank one right-clicked on a living mob is imprinted at once
 * with its species; it is data, never consumed by the Bio Synthesizer, and
 * duplicates by crafting (blank + imprinted). Species in the
 * {@code krios_industry:engram_blacklist} entity tag (empty by default —
 * the player or pack maker decides) refuse the imprint.
 */
public class EngramItem extends Item {

    public static final TagKey<EntityType<?>> BLACKLIST =
            TagKey.create(Registries.ENTITY_TYPE, Constants.id("engram_blacklist"));

    public EngramItem(Properties properties) {
        super(properties);
    }

    /** The imprinted species, or null for a blank (or unknown) Engram. */
    @Nullable
    public static EntityType<?> species(ItemStack stack) {
        ResourceLocation id = stack.get(ModDataComponents.ENGRAM.get());
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    public static boolean isImprinted(ItemStack stack) {
        return stack.getItem() instanceof EngramItem && species(stack) != null;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (stack.has(ModDataComponents.ENGRAM.get()) || !(target instanceof Mob)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        EntityType<?> type = target.getType();
        if (type.is(BLACKLIST)) {
            player.displayClientMessage(Component.translatable("item.krios_industry.engram.refused",
                    type.getDescription()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        ItemStack imprinted = stack.copyWithCount(1);
        imprinted.set(ModDataComponents.ENGRAM.get(), BuiltInRegistries.ENTITY_TYPE.getKey(type));
        stack.shrink(1);
        if (stack.isEmpty()) {
            player.setItemInHand(hand, imprinted);
        } else if (!player.getInventory().add(imprinted)) {
            player.drop(imprinted, false);
        }
        player.level().playSound(null, target, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        player.displayClientMessage(Component.translatable("item.krios_industry.engram.imprinted_now",
                type.getDescription()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        EntityType<?> type = species(stack);
        return type == null ? super.getName(stack)
                : Component.translatable("item.krios_industry.engram.imprinted", type.getDescription());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return species(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(species(stack) == null
                ? "item.krios_industry.engram.blank_hint" : "item.krios_industry.engram.imprinted_hint")
                .withStyle(ChatFormatting.GRAY));
    }
}
