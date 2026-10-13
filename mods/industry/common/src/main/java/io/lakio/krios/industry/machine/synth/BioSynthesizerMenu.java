package io.lakio.krios.industry.machine.synth;

import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.zone.CreatureZoneMenu;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import org.jetbrains.annotations.Nullable;

/** Bio Synthesizer menu: the Engram slot, no buffer, the bay, census and cap data. */
public class BioSynthesizerMenu extends CreatureZoneMenu {

    /** Client constructor. */
    public BioSynthesizerMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.BIO_SYNTHESIZER.get(), containerId, playerInventory, BioSynthesizerBlockEntity.ENGRAM_SLOTS,
                0, BioSynthesizerBlockEntity.DATA_COUNT, EngramItem::isImprinted, ModuleType::isProcessAxis);
    }

    public BioSynthesizerMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.BIO_SYNTHESIZER.get(), containerId, playerInventory, container, data,
                BioSynthesizerBlockEntity.ENGRAM_SLOTS, 0, EngramItem::isImprinted, ModuleType::isProcessAxis);
    }

    /** Mob cap of the zone at the effective tier. */
    public int cap() {
        return Math.max(1, data(BioSynthesizerBlockEntity.DATA_CAP));
    }

    /** The Engram's species, or null in biome mode. */
    @Nullable
    public EntityType<?> engramSpecies() {
        return EngramItem.species(getSlot(0).getItem());
    }
}
