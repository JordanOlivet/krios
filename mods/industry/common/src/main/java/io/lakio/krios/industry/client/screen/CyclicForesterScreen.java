package io.lakio.krios.industry.client.screen;

import io.lakio.krios.industry.machine.forester.CyclicForesterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CyclicForesterScreen extends ZoneMachineScreen<CyclicForesterMenu> {

    public CyclicForesterScreen(CyclicForesterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected String voiceKey() {
        return "cyclic_forester";
    }

    @Override
    protected String plantableKey() {
        return "saplings";
    }
}
