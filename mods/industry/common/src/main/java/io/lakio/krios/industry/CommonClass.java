package io.lakio.krios.industry;

import io.lakio.krios.core.koios.CodexEntries;
import io.lakio.krios.core.koios.KoiosUnlocks;
import io.lakio.krios.core.platform.Services;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.registry.ModBlocks;
import io.lakio.krios.industry.registry.ModCreativeTabs;
import io.lakio.krios.industry.registry.ModDataComponents;
import io.lakio.krios.industry.registry.ModItems;
import io.lakio.krios.industry.registry.ModMenus;
import io.lakio.krios.industry.registry.ModRecipeTypes;

public class CommonClass {

    // Shared bootstrap invoked by each loader entry point. Registration order
    // matters: blocks before items (BlockItems), items before tabs (tab icon).
    public static void init() {
        ModDataComponents.init();
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModRecipeTypes.init();
        ModCreativeTabs.init();
        registerCodex();
        registerUnlocks();
        Constants.LOG.info("{} initialized on {}", Constants.MOD_NAME, Services.PLATFORM.getPlatformName());
    }

    /** The machines' Codex entries, between core's opening and closing ones (band 100-299). */
    private static void registerCodex() {
        String prefix = "gui." + Constants.MOD_ID + ".codex.";
        String[] machines = {"thermal_reactor", "photon_array", "fractal_grinder", "plasma_forge",
                "chrono_farmer", "cyclic_forester", "quantum_lure", "breeding_pod", "harvest_pod",
                "phase_death_field", "bio_synthesizer"};
        for (int i = 0; i < machines.length; i++) {
            CodexEntries.register(100 + i, prefix + machines[i], 1, false);
        }
        CodexEntries.register(200, prefix + "modules", 2, true);
    }

    /** The module rungs the core terminal lists under UNLOCKS, per tier. */
    private static void registerUnlocks() {
        String prefix = "gui." + Constants.MOD_ID + ".koios.unlock.";
        KoiosUnlocks.register(1, IndustryBalance.SPEED_UNLOCK_LEVEL, prefix + "speed");
        KoiosUnlocks.register(1, IndustryBalance.EFFICIENCY_UNLOCK_LEVEL, prefix + "efficiency");
        KoiosUnlocks.register(1, IndustryBalance.YIELD_UNLOCK_LEVEL, prefix + "yield");
        KoiosUnlocks.register(2, IndustryBalance.SPEED2_UNLOCK_LEVEL, prefix + "speed2");
        KoiosUnlocks.register(2, IndustryBalance.ENERGY_UNLOCK_LEVEL, prefix + "energy");
        KoiosUnlocks.register(2, IndustryBalance.EFFICIENCY2_UNLOCK_LEVEL, prefix + "efficiency2");
        KoiosUnlocks.register(2, IndustryBalance.YIELD2_UNLOCK_LEVEL, prefix + "yield2");
    }
}
