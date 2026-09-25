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
 * formed core in range has reached its level.
 */
public class ModuleItem extends Item {

    private final ModuleType type;

    public ModuleItem(ModuleType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public ModuleType getType() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.krios_industry.module.requires_level", type.requiredLevel())
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
