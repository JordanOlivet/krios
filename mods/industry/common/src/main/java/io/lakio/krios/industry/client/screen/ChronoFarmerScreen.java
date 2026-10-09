package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.machine.farmer.ChronoFarmerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ChronoFarmerScreen extends PlantingZoneScreen<ChronoFarmerMenu> {

    public ChronoFarmerScreen(ChronoFarmerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected String voiceKey() {
        return "chrono_farmer";
    }

    @Override
    protected String plantableKey() {
        return "seeds";
    }
}
