package io.lakio.krios.industry.machine.crusher;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.machine.generator.GeneratorBlockEntity;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CrusherBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int CAPACITY = 20_000;
    public static final int PULL_PER_TICK = 400;

    private static final int[] SLOTS_TOP_SIDES = {INPUT_SLOT};
    private static final int[] SLOTS_DOWN = {OUTPUT_SLOT};

    private final EnergyBuffer energy = new EnergyBuffer(CAPACITY, 0);
    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private final RecipeManager.CachedCheck<SingleRecipeInput, CrushingRecipe> quickCheck =
            RecipeManager.createCheck(ModRecipeTypes.CRUSHING.get());
    private int progress;
    private int totalTime;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> totalTime;
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

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUSHER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrusherBlockEntity be) {
        be.pullEnergyFromNeighbors(level, pos);

        RecipeHolder<CrushingRecipe> holder = be.items.get(INPUT_SLOT).isEmpty() ? null
                : be.quickCheck.getRecipeFor(new SingleRecipeInput(be.items.get(INPUT_SLOT)), level).orElse(null);
        if (holder == null || !be.canOutput(holder.value().result())) {
            be.stopWork(level, pos, state);
            return;
        }

        CrushingRecipe recipe = holder.value();
        be.totalTime = recipe.time();
        int energyPerTick = Math.max(1, recipe.energy() / recipe.time());
        if (be.energy.consume(energyPerTick)) {
            if (++be.progress >= recipe.time()) {
                be.craft(recipe);
                be.progress = 0;
            }
        }
        // No energy: stay lit and wait — neighbors may refill next tick.
        be.setChanged();
    }

    private void pullEnergyFromNeighbors(Level level, BlockPos pos) {
        if (energy.isFull()) {
            return;
        }
        // Direct adjacency pull, v0 energy transport. Replaced by a proper
        // network (with scheduled wake-ups) later.
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof GeneratorBlockEntity generator) {
                int pulled = generator.getEnergy().extract(
                        Math.min(PULL_PER_TICK, energy.getCapacity() - energy.getStored()));
                if (pulled > 0) {
                    energy.insert(pulled);
                    generator.setChanged();
                }
                if (energy.isFull()) {
                    return;
                }
            }
        }
    }

    private boolean canOutput(ItemStack result) {
        ItemStack current = items.get(OUTPUT_SLOT);
        if (current.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(current, result)
                && current.getCount() + result.getCount() <= current.getMaxStackSize();
    }

    /**
     * Yield-multiplier hook (evolving multiplier, ADR/design): when modules
     * exist, the crafted count becomes result.getCount() scaled by the
     * machine's multiplier. Base machine crafts the recipe as written.
     */
    private void craft(CrushingRecipe recipe) {
        ItemStack result = recipe.result();
        items.get(INPUT_SLOT).shrink(1);
        ItemStack current = items.get(OUTPUT_SLOT);
        if (current.isEmpty()) {
            items.set(OUTPUT_SLOT, result.copy());
        } else {
            current.grow(result.getCount());
        }
    }

    private void stopWork(Level level, BlockPos pos, BlockState state) {
        progress = 0;
        setChanged();
        level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
    }

    /** Called when input lands while the crusher is off. */
    private void tryWake() {
        if (level != null && !level.isClientSide
                && !getBlockState().getValue(BlockStateProperties.LIT)
                && !items.get(INPUT_SLOT).isEmpty()) {
            level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, true), 3);
        }
    }

    public EnergyBuffer getEnergy() {
        return energy;
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.crusher");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CrusherMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- WorldlyContainer (hoppers: input top/sides, output bottom) ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? SLOTS_DOWN : SLOTS_TOP_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == INPUT_SLOT;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT_SLOT;
    }

    @Override
    public int getContainerSize() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        return items.get(INPUT_SLOT).isEmpty() && items.get(OUTPUT_SLOT).isEmpty();
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
        if (slot == INPUT_SLOT) {
            tryWake();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT_SLOT;
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
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
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy.load(tag);
        progress = tag.getInt("Progress");
    }
}
