package io.lakio.krios.industry.machine.generator;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosNetwork;
import io.lakio.krios.industry.platform.Services;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class GeneratorBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int CAPACITY = 100_000;
    public static final int GENERATION_PER_TICK = 40;
    public static final int MAX_EXTRACT_PER_TICK = 400;

    // Primitive runtime state (ADR-012). No object allocation on the tick path.
    private final EnergyBuffer energy = new EnergyBuffer(CAPACITY, MAX_EXTRACT_PER_TICK);
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int burnTime;
    private int burnDuration;

    // ContainerData values are truncated to 16-bit shorts on sync, so the
    // energy value ships as a low/high pair. Capacity is a constant and is
    // never synced.
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> burnDuration;
                case 2 -> energy.getStored() & 0xFFFF;
                case 3 -> (energy.getStored() >> 16) & 0xFFFF;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; menus never write back.
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity be) {
        // Only reachable while LIT (the block registers no ticker otherwise).
        be.energy.insert(GENERATION_PER_TICK);
        if (--be.burnTime <= 0 && !be.tryConsumeFuel()) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
        }
        be.setChanged();
    }

    /** Starts the next burn cycle if fuel is available and the buffer has room. */
    private boolean tryConsumeFuel() {
        if (energy.isFull()) {
            return false;
        }
        ItemStack fuel = items.get(0);
        int time = Services.PLATFORM.getBurnTime(fuel);
        if (time <= 0) {
            return false;
        }
        // Burn cycles feed the KOIOS diversity window keyed by fuel item, so
        // varied fuels teach more than a coal monoculture.
        if (level != null) {
            KoiosNetwork.reportOperations(level, worldPosition,
                    "burn:" + BuiltInRegistries.ITEM.getKey(fuel.getItem()), 1, KoiosBalance.BURN_BASE_XP);
        }
        fuel.shrink(1);
        burnTime = time;
        burnDuration = time;
        return true;
    }

    /** Called when fuel lands in the slot while the generator is off. */
    private void tryIgnite() {
        if (level != null && !level.isClientSide
                && !getBlockState().getValue(BlockStateProperties.LIT)
                && tryConsumeFuel()) {
            level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, true), 3);
        }
    }

    public EnergyBuffer getEnergy() {
        return energy;
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GeneratorMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- Container (1 fuel slot; gives hopper compat for free) ---

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
        tryIgnite();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return Services.PLATFORM.getBurnTime(stack) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        energy.save(tag);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnDuration", burnDuration);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy.load(tag);
        burnTime = tag.getInt("BurnTime");
        burnDuration = tag.getInt("BurnDuration");
    }
}
