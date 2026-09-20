package io.lakio.krios.industry;

import io.lakio.krios.industry.platform.Services;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.registry.ModBlocks;
import io.lakio.krios.industry.registry.ModCreativeTabs;
import io.lakio.krios.industry.registry.ModItems;
import io.lakio.krios.industry.registry.ModMenus;

public class CommonClass {

    // Shared bootstrap invoked by each loader entry point. Registration order
    // matters: blocks before items (BlockItems), items before tabs (tab icon).
    public static void init() {
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModCreativeTabs.init();
        Constants.LOG.info("{} initialized on {}", Constants.MOD_NAME, Services.PLATFORM.getPlatformName());
    }
}
