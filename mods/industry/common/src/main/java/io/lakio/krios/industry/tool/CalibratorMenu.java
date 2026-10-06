package io.lakio.krios.industry.tool;

import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.machine.EnergyReport;
import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.FlowPorts;
import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkManager;
import io.lakio.krios.industry.network.NetworkNode;
import io.lakio.krios.industry.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Display-only network diagnostic, opened by the Calibrator on any network
 * block. The anchor is a position, not a block entity: the readout follows
 * whatever component contains that block, live.
 */
public class CalibratorMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 13;

    public static final int STATUS_NONE = 0;
    public static final int STATUS_ONLINE = 1;
    public static final int STATUS_ORPHAN = 2;
    public static final int STATUS_FAULT = 3;

    private final ContainerData data;
    @Nullable
    private final Level level;
    @Nullable
    private final BlockPos pos;


    /** Client constructor. */
    public CalibratorMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.CALIBRATOR.get(), containerId);
        this.level = null;
        this.pos = null;
        this.data = new SimpleContainerData(DATA_COUNT);
        addDataSlots(data);
    }

    /** Server constructor: reads the component at {@code pos} on every sync. */
    public CalibratorMenu(int containerId, Inventory playerInventory, Level level, BlockPos pos) {
        super(ModMenus.CALIBRATOR.get(), containerId);
        this.level = level;
        this.pos = pos.immutable();
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                Network network = NetworkManager.networkAt(level, pos);
                if (network == null) {
                    return 0;
                }
                return switch (index) {
                    case 0 -> network.isFault() ? STATUS_FAULT
                            : network.isValid() ? STATUS_ONLINE : STATUS_ORPHAN;
                    case 1 -> network.members().size();
                    case 2 -> network.size();
                    case 3 -> network.controller() instanceof KoiosCoreBlockEntity core
                            ? core.getKoiosLevel() : 0;
                    // 4..7: SYSTEM totals (all machine buffers), not just the
                    // producer pool — what the player thinks of as "stored".
                    case 4 -> EnergyReport.stored(network) & 0xFFFF;
                    case 5 -> (EnergyReport.stored(network) >> 16) & 0xFFFF;
                    case 6 -> EnergyReport.capacity(network) & 0xFFFF;
                    case 7 -> (EnergyReport.capacity(network) >> 16) & 0xFFFF;
                    case 8 -> countHintMembers(network);
                    case 9 -> EnergyReport.supply(network);
                    case 10 -> EnergyReport.draw(network);
                    case 11 -> EnergyReport.consumed(network);
                    case 12 -> EnergyReport.starvedCount(network);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Server-authoritative; menus never write back.
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addDataSlots(data);
    }

    private static int countHintMembers(Network network) {
        int count = 0;
        for (NetworkNode member : network.members()) {
            if (member instanceof FlowPorts ports) {
                for (Direction direction : Direction.values()) {
                    if (ports.faceConfig().hasHint(direction)) {
                        count++;
                        break;
                    }
                }
            }
        }
        return count;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return pos == null
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    public int status() {
        return data.get(0);
    }

    public int machineCount() {
        return data.get(1);
    }

    public int blockCount() {
        return data.get(2);
    }

    public int controllerLevel() {
        return data.get(3);
    }

    /** Reassembled from the low/high 16-bit pair (ContainerData syncs shorts). */
    public int energyStored() {
        return (data.get(5) << 16) | (data.get(4) & 0xFFFF);
    }

    public int energyCapacity() {
        return (data.get(7) << 16) | (data.get(6) & 0xFFFF);
    }

    public int hintMembers() {
        return data.get(8);
    }

    /** Network production in E/t (burning reactors). */
    public int supplyPerTick() {
        return data.get(9);
    }

    /** Network demand in E/t (working machines). */
    public int drawPerTick() {
        return data.get(10);
    }

    /** Energy actually consumed network-wide, in E/t (measured, not demand). */
    public int consumedPerTick() {
        return data.get(11);
    }

    /** Machines frozen for lack of energy right now. */
    public int starvedCount() {
        return data.get(12);
    }
}
