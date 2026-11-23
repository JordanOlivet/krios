package io.lakio.krios.core.integration.emi;

import java.util.List;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import io.lakio.krios.core.Constants;
import io.lakio.krios.core.client.screen.KriosTerminalScreen;
import io.lakio.krios.core.registry.ModBlocks;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/**
 * EMI integration of core (optional, client-only; EMI finds this class by
 * its annotation on NeoForge and by the {@code emi} entrypoint on Fabric).
 * Same scope as the JEI plugin: the terminals' off-frame areas, and a word
 * on the core multiblock.
 */
@EmiEntrypoint
public class KriosCoreEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea((screen, consumer) -> {
            if (screen instanceof KriosTerminalScreen<?> terminal) {
                for (Rect2i area : terminal.extraAreas()) {
                    consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
                }
            }
        });
        // Synthetic recipe: EMI's convention is an id path starting with a slash.
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(ModBlocks.KOIOS_CORE.get())),
                List.of(Component.translatable("block.krios_core.koios_core.hint")),
                Constants.id("/info/koios_core")));
    }
}
