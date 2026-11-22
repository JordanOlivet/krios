package io.lakio.krios.core;

import io.lakio.krios.core.koios.CodexEntries;
import io.lakio.krios.core.platform.Services;
import io.lakio.krios.core.registry.ModBlockEntities;
import io.lakio.krios.core.registry.ModBlocks;
import io.lakio.krios.core.registry.ModCreativeTabs;
import io.lakio.krios.core.registry.ModDataComponents;
import io.lakio.krios.core.registry.ModItems;
import io.lakio.krios.core.registry.ModMenus;

public class CommonClass {

    // Shared bootstrap invoked by each loader entry point. Registration order
    // matters: blocks before items (BlockItems), items before tabs (tab icon).
    public static void init() {
        ModDataComponents.init();
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModCreativeTabs.init();
        registerCodex();
        Constants.LOG.info("{} initialized on {}", Constants.MOD_NAME, Services.PLATFORM.getPlatformName());
    }

    /** The Codex entries core owns; content mods slot theirs in between (see {@link CodexEntries}). */
    private static void registerCodex() {
        String prefix = "gui." + Constants.MOD_ID + ".codex.";
        CodexEntries.register(0, prefix + "koios", 1, true);
        CodexEntries.register(10, prefix + "progression", 1, false);
        CodexEntries.register(20, prefix + "diversity", 1, false);
        CodexEntries.register(800, prefix + "network", 1, true);
        CodexEntries.register(810, prefix + "conduit", 1, false);
        CodexEntries.register(820, prefix + "calibrator", 1, false);
        CodexEntries.register(830, prefix + "critical", 4, false);
    }
}
