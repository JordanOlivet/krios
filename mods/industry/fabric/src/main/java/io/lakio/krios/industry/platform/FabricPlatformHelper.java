package io.lakio.krios.industry.platform;

import io.lakio.krios.industry.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public int getBurnTime(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        // Fabric API's FuelRegistry feeds this same vanilla map.
        Integer time = AbstractFurnaceBlockEntity.getFuel().get(stack.getItem());
        return time == null ? 0 : time;
    }

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
