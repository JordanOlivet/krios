package io.lakio.krios.industry.integration;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/** Texts both recipe viewer plugins show for a machine recipe. */
public final class ViewerText {

    private ViewerText() {
    }

    /** Energy of one operation, before the world's difficulty multiplier. */
    public static Component energy(int energy) {
        return Component.translatable("gui.krios_industry.viewer.energy", energy);
    }

    /** Duration of one operation in seconds ("8", "2.5"), from ticks. */
    public static Component time(int ticks) {
        String seconds = ticks % 20 == 0
                ? Integer.toString(ticks / 20)
                : String.format(Locale.ROOT, "%.1f", ticks / 20.0);
        return Component.translatable("gui.krios_industry.viewer.time", seconds);
    }

    /** What the grinding result count stands for (tier 1, connected). */
    public static Component grindingYield() {
        return Component.translatable("gui.krios_industry.viewer.grinding.yield");
    }
}
