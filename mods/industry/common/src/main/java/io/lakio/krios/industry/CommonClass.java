package io.lakio.krios.industry;

import io.lakio.krios.industry.platform.Services;
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
        io.lakio.krios.industry.dev.DevBench.init();
        ModMenus.init();
        ModRecipeTypes.init();
        ModCreativeTabs.init();
        Constants.LOG.info("{} initialized on {}", Constants.MOD_NAME, Services.PLATFORM.getPlatformName());
    }
}
