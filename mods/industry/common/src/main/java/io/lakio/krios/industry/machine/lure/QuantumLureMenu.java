package io.lakio.krios.industry.machine.lure;

import io.lakio.krios.industry.machine.zone.ZoneMachineBlockEntity;
import io.lakio.krios.industry.machine.zone.ZoneMachineMenu;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Quantum Lure menu: no inputs, the buffer, the bay and the line data. */
public class QuantumLureMenu extends ZoneMachineMenu {

    /** Client constructor. */
    public QuantumLureMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.QUANTUM_LURE.get(), containerId, playerInventory, 0,
                ZoneMachineBlockEntity.BUFFER_SLOTS, QuantumLureBlockEntity.DATA_COUNT,
                stack -> false, QuantumLureBlockEntity::acceptsModule);
    }

    public QuantumLureMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.QUANTUM_LURE.get(), containerId, playerInventory, container, data, 0,
                ZoneMachineBlockEntity.BUFFER_SLOTS, stack -> false, QuantumLureBlockEntity::acceptsModule);
    }

    /** QuantumLureBlockEntity.WATER_* reading of the last wake. */
    public int waterStatus() {
        return data(QuantumLureBlockEntity.DATA_WATER);
    }

    /** Lines of the effective tier (1/2/4/8). */
    public int lines() {
        return Math.max(1, Math.min(QuantumLureBlockEntity.MAX_LINES, data(QuantumLureBlockEntity.DATA_LINES)));
    }

    /** Line progress toward its next catch, permille. */
    public int lineProgress(int line) {
        return data(QuantumLureBlockEntity.DATA_PROGRESS + line);
    }

    /** Bit i set when zone cell i can fish (25 bits, low/high short pair). */
    public int waterMask() {
        return (data(QuantumLureBlockEntity.DATA_MASK + 1) << 16)
                | (data(QuantumLureBlockEntity.DATA_MASK) & 0xFFFF);
    }
}
