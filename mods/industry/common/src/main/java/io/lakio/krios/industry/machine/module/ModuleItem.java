package io.lakio.krios.industry.machine.module;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Machine upgrade module. Crafting is open (datapack recipes, ADR-007); the
 * KOIOS gate applies on insertion — a machine only accepts a module when a
 * formed core in range has reached its level. Points model (2026-10-01):
 * the item carries its axis, its point weight and its own unlock level.
 */
public class ModuleItem extends Item {

    private final ModuleType type;
    private final int points;
    private final int requiredLevel;

    public ModuleItem(ModuleType type, int points, int requiredLevel, Properties properties) {
        super(properties);
        this.type = type;
        this.points = points;
        this.requiredLevel = requiredLevel;
    }

    public ModuleType getType() {
        return type;
    }

    /** Weight on the axis: Mk I = 1, Mk II = 2. */
    public int points() {
        return points;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.krios_industry.module.requires_level", requiredLevel)
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
