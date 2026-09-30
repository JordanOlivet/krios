package io.lakio.krios.industry.machine.furnace;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.machine.generator.GeneratorBlockEntity;
import io.lakio.krios.industry.machine.ItemFlow;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.network.EnergySink;
import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.Flow;
import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkManager;
import io.lakio.krios.industry.network.NetworkNode;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Energy-powered smelter over VANILLA smelting recipes (design: automate
 * and accelerate the classic processes). Connected to a KOIOS network it
 * smelts at twice furnace speed with module support; offline it falls back
 * to exact vanilla parity (200-tick pace, 1:1, modules inert). Same
 * tickless-idle, face-config and wake contracts as the crusher.
 */
public class FurnaceBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, NetworkNode, EnergySink,
        io.lakio.krios.industry.machine.ConfigurableFaces,
        io.lakio.krios.industry.machine.EnergyStats {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int MODULE_SLOT_FIRST = 2;
    /** Tier knob: bump the count and menu/screen/bay all follow. */
    public static final int MODULE_SLOT_COUNT = 2;
    public static final int MODULE_SLOT_LAST = MODULE_SLOT_FIRST + MODULE_SLOT_COUNT - 1;
    public static final int INVENTORY_SIZE = MODULE_SLOT_LAST + 1;
    public static final int CAPACITY = 20_000;
    public static final int PULL_PER_TICK = 400;
    /** Flat energy per smelt (vanilla recipes carry no energy cost). */
    public static final int ENERGY_PER_OP = 400;

    // Efficiency: x0.8 energy per module, as a 25ths ratio (integer math).
    private static final int[] EFFICIENCY_NUM = {25, 20, 16};

    private static final int[] SLOTS_TOP_SIDES = {INPUT_SLOT};
    private static final int[] SLOTS_DOWN = {OUTPUT_SLOT};
    private static final int[] NO_SLOTS = {};

    // Presets: directions this machine supports (energy in, items in/out).
    private static final short PRESET_IN_BITS = (short) (FaceConfig.flowBits(Flow.ENERGY, false)
            | FaceConfig.flowBits(Flow.ITEM, false));
    private static final short PRESET_OUT_BITS = (short) FaceConfig.flowBits(Flow.ITEM, true);
    /** Single source for the GUI hologram columns and the in-world overlay. */
    public static final short SUPPORTED_FACE_BITS = (short) (PRESET_IN_BITS | PRESET_OUT_BITS);

    private final EnergyBuffer energy = new EnergyBuffer(CAPACITY, 0);
    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final FaceConfig faceConfig = new FaceConfig();
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> quickCheck =
            RecipeManager.createCheck(RecipeType.SMELTING);
    private int progress;
    private int totalTime;
    /** Current demand in E/t, for diagnostics; 0 while idle. */
    private int currentDrawPerTick;
    /** E actually consumed on the last work tick (0 = idle or starved). */
    private int consumedLastTick;
    /** Working but the last consume failed — frozen for lack of energy. */
    private boolean starvedLastTick;
    // Yield accumulator in quarter-items (ADR-006, same scheme as the crusher).
    private int yieldQuarters;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> totalTime;
                case 2 -> energy.getStored() & 0xFFFF;
                case 3 -> (energy.getStored() >> 16) & 0xFFFF;
                case 10 -> networkKoiosLevel();
                // 11..15: machine pos for the hologram view.
                case 11 -> worldPosition.getX() & 0xFFFF;
                case 12 -> (worldPosition.getX() >> 16) & 0xFFFF;
                case 13 -> worldPosition.getZ() & 0xFFFF;
                case 14 -> (worldPosition.getZ() >> 16) & 0xFFFF;
                case 15 -> worldPosition.getY();
                case 16 -> currentDrawPerTick;
                case 17 -> starvedLastTick ? 1 : 0;
                // 4..9: raw face-config shorts (11 bits, short-safe).
                default -> index < 10
                        ? faceConfig.bits(Direction.from3DDataValue(index - 4)) : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; menus never write back.
        }

        @Override
        public int getCount() {
            return FurnaceMenu.DATA_COUNT;
        }
    };

    public FurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FURNACE.get(), pos, state);
    }

    /** Same button channel as the other machines (face*16 + sub). */
    public boolean handleFaceButton(int id) {
        int faceIndex = id / 16;
        int sub = id % 16;
        if (faceIndex < 0 || faceIndex > 5) {
            return false;
        }
        Direction face = Direction.from3DDataValue(faceIndex);
        if (sub == 15) {
            if (faceConfig.isManual(face)) {
                faceConfig.resetToVirgin(face);
            } else {
                faceConfig.enterManual(face, (short) (PRESET_IN_BITS | PRESET_OUT_BITS));
            }
        } else if (sub >= 9 && sub <= 12) {
            faceConfig.enterManual(face, (short) switch (sub) {
                case 9 -> 0;
                case 10 -> PRESET_IN_BITS;
                case 11 -> PRESET_OUT_BITS;
                default -> PRESET_IN_BITS | PRESET_OUT_BITS;
            });
        } else if (sub < 8 && faceConfig.isManual(face)) {
            faceConfig.toggleManual(face, Flow.VALUES[sub / 2], sub % 2 == 1);
        } else {
            return false;
        }
        setChanged();
        syncFacesToClients();
        return true;
    }

    // --- Network node ---

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide) {
            NetworkManager.addNode(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            NetworkManager.removeNode(this);
        }
    }

    @Override
    public BlockPos networkPos() {
        return worldPosition;
    }

    @Override
    public Level networkLevel() {
        return level;
    }

    /** KOIOS level of this machine's network controller; 0 while offline. */
    public int networkKoiosLevel() {
        Network network = NetworkManager.networkOf(this);
        return network != null && network.controller() instanceof KoiosCoreBlockEntity core
                ? core.getKoiosLevel() : 0;
    }

    // --- Flow ports: energy sink on every face (same as the crusher) ---

    @Override
    public boolean canOffer(Direction face, Flow flow) {
        return false;
    }

    @Override
    public boolean canAccept(Direction face, Flow flow) {
        return flow == Flow.ENERGY;
    }

    @Override
    public FaceConfig faceConfig() {
        return faceConfig;
    }

    @Override
    public short supportedFaceBits() {
        return SUPPORTED_FACE_BITS;
    }

    @Override
    public void faceConfigDirty() {
        // Never setChanged() here: auto-config can run during chunk load
        // and the comparator scan would sync-load chunks (deadlock).
        if (level != null) {
            NetworkManager.markUnsaved(level, worldPosition);
            syncFacesToClients();
        }
    }

    /**
     * Face contract to watching clients (in-world Calibrator overlay).
     * Safe where setChanged is not: blockChanged only queues on an already
     * present chunk holder, no comparator scan, no chunk loading.
     */
    private void syncFacesToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FurnaceBlockEntity be) {
        Network network = NetworkManager.networkOf(be);
        boolean online = network != null && network.isValid();
        // Online energy arrives through the producer's fair-shared push only
        // (a per-machine pull caused sequential 100% buffer charges).
        if (!online) {
            be.pullEnergyFromContacts(level, pos);
        }
        // Hopper-like flow: the output streams into adjacent machines.
        ItemFlow.pushOutput(level, pos, be, OUTPUT_SLOT, be.faceConfig);

        RecipeHolder<SmeltingRecipe> holder = be.items.get(INPUT_SLOT).isEmpty() ? null
                : be.quickCheck.getRecipeFor(new SingleRecipeInput(be.items.get(INPUT_SLOT)), level).orElse(null);
        ItemStack result = holder == null ? ItemStack.EMPTY
                : holder.value().getResultItem(level.registryAccess());
        int yieldModules = holder == null || !online ? 0 : be.countModules(ModuleType.YIELD);
        if (holder == null || result.isEmpty() || !be.canOutput(result, yieldModules)) {
            be.stopWork(level, pos, state);
            return;
        }

        int cookingTime = Math.max(1, holder.value().getCookingTime());
        int speedModules = online ? be.countModules(ModuleType.SPEED) : 0;
        int efficiencyModules = online ? be.countModules(ModuleType.EFFICIENCY) : 0;
        // Connected baseline: twice furnace speed, then x1.5 per speed
        // module — cook / (2 + n). Offline: exact vanilla pace.
        be.totalTime = online
                ? Math.max(1, cookingTime / (2 + speedModules))
                : Math.max(cookingTime, KoiosBalance.DISCONNECTED_TIME_TICKS);
        int energyPerOp = KoiosBalance.scaledEnergyCost(level,
                ENERGY_PER_OP * EFFICIENCY_NUM[efficiencyModules] / 25);
        int energyPerTick = Math.max(1, energyPerOp / be.totalTime);
        be.currentDrawPerTick = energyPerTick;
        boolean starvedNow;
        if (be.energy.consume(energyPerTick)) {
            be.consumedLastTick = energyPerTick;
            starvedNow = false;
            if (++be.progress >= be.totalTime) {
                be.craft(holder, result, yieldModules);
                be.progress = 0;
            }
        } else {
            // No energy: stay lit and wait — the next push may refill us.
            be.consumedLastTick = 0;
            starvedNow = true;
        }
        if (starvedNow != be.starvedLastTick) {
            be.starvedLastTick = starvedNow;
            be.notifyControllerEnergyState();
        }
        be.setChanged();
    }

    /** Starved-state transition: let the KOIOS screen resync its alert. */
    private void notifyControllerEnergyState() {
        Network network = NetworkManager.networkOf(this);
        if (network != null && network.controller() instanceof KoiosCoreBlockEntity core) {
            core.energyStateChanged();
        }
    }

    private int countModules(ModuleType type) {
        int count = 0;
        for (int slot = MODULE_SLOT_FIRST; slot <= MODULE_SLOT_LAST; slot++) {
            if (items.get(slot).getItem() instanceof ModuleItem module && module.getType() == type) {
                count++;
            }
        }
        return count;
    }

    /** Offline contact transfer, same contract as the crusher. */
    private void pullEnergyFromContacts(Level level, BlockPos pos) {
        if (energy.isFull()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (!allowsEnergyIn(direction)) {
                continue;
            }
            if (level.getBlockEntity(pos.relative(direction)) instanceof GeneratorBlockEntity generator
                    && generator.allowsEnergyExtract(direction.getOpposite())) {
                int pulled = generator.extractNetworkEnergy(
                        Math.min(PULL_PER_TICK, energy.getCapacity() - energy.getStored()));
                if (pulled > 0) {
                    energy.insert(pulled);
                }
                if (energy.isFull()) {
                    return;
                }
            }
        }
    }

    private boolean allowsEnergyIn(Direction face) {
        return !faceConfig.isManual(face) || faceConfig.allows(face, Flow.ENERGY, false);
    }

    /** Vanilla smelting is 1:1; connected yield modules add +25% each. */
    private static int craftQuarters(ItemStack result, int yieldModules) {
        return result.getCount() * (4 + yieldModules);
    }

    private boolean canOutput(ItemStack result, int yieldModules) {
        int prospective = (yieldQuarters + craftQuarters(result, yieldModules)) / 4;
        ItemStack current = items.get(OUTPUT_SLOT);
        if (current.isEmpty()) {
            return prospective <= result.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(current, result)
                && current.getCount() + prospective <= current.getMaxStackSize();
    }

    private void craft(RecipeHolder<SmeltingRecipe> holder, ItemStack result, int yieldModules) {
        yieldQuarters += craftQuarters(result, yieldModules);
        int crafted = yieldQuarters / 4;
        yieldQuarters %= 4;
        items.get(INPUT_SLOT).shrink(1);
        ItemStack current = items.get(OUTPUT_SLOT);
        if (current.isEmpty()) {
            ItemStack out = result.copy();
            out.setCount(crafted);
            items.set(OUTPUT_SLOT, out);
        } else {
            current.grow(crafted);
        }
        Network network = NetworkManager.networkOf(this);
        if (network != null && network.controller() instanceof KoiosCoreBlockEntity core) {
            core.reportOperations(holder.id().toString(), 1, KoiosBalance.SMELT_BASE_XP);
        }
    }

    private void stopWork(Level level, BlockPos pos, BlockState state) {
        progress = 0;
        currentDrawPerTick = 0;
        consumedLastTick = 0;
        if (starvedLastTick) {
            starvedLastTick = false;
            notifyControllerEnergyState();
        }
        setChanged();
        level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
    }

    // --- Energy diagnostics (Calibrator, KOIOS alerts) ---

    @Override
    public int energyDrawPerTick() {
        return currentDrawPerTick;
    }

    @Override
    public int energySupplyPerTick() {
        return 0;
    }

    @Override
    public int energyConsumedPerTick() {
        return consumedLastTick;
    }

    @Override
    public boolean energyStarved() {
        return starvedLastTick;
    }

    @Override
    public int energyStored() {
        return energy.getStored();
    }

    @Override
    public int energyCapacity() {
        return energy.getCapacity();
    }

    /** Called when any slot changes while the furnace is off. */
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

    /** Producers push proactively; same rate cap as our own pulls. */
    @Override
    public int insertNetworkEnergy(int max) {
        return energy.insert(Math.min(max, PULL_PER_TICK));
    }

    /** Working machines are served before idle buffers (surplus tier). */
    @Override
    public int sinkPriority() {
        return getBlockState().getValue(BlockStateProperties.LIT) ? 0 : 1;
    }

    /** A neighbor changed (placed/removed/mutated): try to flow and wake. */
    public void onNeighborChanged() {
        if (level != null && !level.isClientSide) {
            ItemFlow.pushOutput(level, worldPosition, this, OUTPUT_SLOT, faceConfig);
            tryWake();
        }
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.furnace");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FurnaceMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- WorldlyContainer: same manual-contract gating as the crusher ---

    private boolean allowsItem(Direction face, boolean out) {
        return !faceConfig.isManual(face) || faceConfig.allows(face, Flow.ITEM, out);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return allowsItem(side, true) ? SLOTS_DOWN : NO_SLOTS;
        }
        return allowsItem(side, false) ? SLOTS_TOP_SIDES : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        // Automated insertion (hoppers, machine flow) only takes what this
        // machine can actually smelt — players stay free in the GUI.
        return slot == INPUT_SLOT && allowsItem(side, false) && isProcessable(stack);
    }

    private boolean isProcessable(ItemStack stack) {
        return level != null
                && quickCheck.getRecipeFor(new SingleRecipeInput(stack), level).isPresent();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT_SLOT && allowsItem(side, true);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        setChanged();
        // Freeing the output slot can unblock a stalled smelt.
        tryWake();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        tryWake();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
        // Any slot change may unblock the machine.
        tryWake();
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

    // --- Client sync: the face contract rides the vanilla BE update tag
    // (chunk load + sendBlockUpdated) for the in-world Calibrator overlay;
    // everything else stays menu-synced. ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        faceConfig.save(tag);
        if (!tag.contains("Faces")) {
            // All-virgin still ships: an empty tag would be dropped and a
            // client that saw manual bits would keep them after a reset.
            tag.putIntArray("Faces", new int[6]);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        energy.save(tag);
        faceConfig.save(tag);
        tag.putInt("Progress", progress);
        tag.putInt("YieldQuarters", yieldQuarters);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy.load(tag);
        faceConfig.load(tag);
        progress = tag.getInt("Progress");
        yieldQuarters = tag.getInt("YieldQuarters");
    }
}
