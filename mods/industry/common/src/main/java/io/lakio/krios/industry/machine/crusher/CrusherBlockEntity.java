package io.lakio.krios.industry.machine.crusher;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.machine.generator.GeneratorBlockEntity;
import io.lakio.krios.industry.machine.ItemFlow;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.TieredMachine;
import io.lakio.krios.industry.network.EnergySink;
import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.Flow;
import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkManager;
import io.lakio.krios.industry.network.NetworkNode;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.registry.ModRecipeTypes;
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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CrusherBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, NetworkNode, EnergySink,
        io.lakio.krios.industry.machine.ConfigurableFaces,
        io.lakio.krios.industry.machine.EnergyStats {

    /**
     * Parallel process columns this machine LINE can host — the tier 2
     * ceiling for now (grows to 4/8 when tiers 3/4 ship, indices reshuffle
     * then). Layout: inputs, then outputs, then modules.
     */
    public static final int PROCESS_SLOTS = 2;
    public static final int INPUT_FIRST = 0;
    public static final int OUTPUT_FIRST = PROCESS_SLOTS;
    public static final int MODULE_SLOT_FIRST = PROCESS_SLOTS * 2;
    /**
     * Physical bay ceiling; the machine tier gates how many slots are
     * ACTIVE (2 per tier — the points model, design 2026-10-01).
     */
    public static final int MODULE_SLOT_COUNT = 4;
    public static final int MODULE_SLOT_LAST = MODULE_SLOT_FIRST + MODULE_SLOT_COUNT - 1;
    public static final int INVENTORY_SIZE = MODULE_SLOT_LAST + 1;
    /** 10 heavy crushes of buffer (energy scale 2026-09-30, revised). */
    public static final int CAPACITY = 8_000;
    public static final int PULL_PER_TICK = 400;

    private static final int[] SLOTS_TOP_SIDES = {0, 1};
    private static final int[] SLOTS_DOWN = {OUTPUT_FIRST, OUTPUT_FIRST + 1};
    private static final int[] NO_SLOTS = {};

    private final int machineTier;
    private final EnergyBuffer energy;
    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final FaceConfig faceConfig = new FaceConfig();
    private final RecipeManager.CachedCheck<SingleRecipeInput, CrushingRecipe> quickCheck =
            RecipeManager.createCheck(ModRecipeTypes.CRUSHING.get());
    // Per-column runtime state (parallel processing, design 2026-09-30):
    // each column is an independent input -> output pair with its own
    // progress and deterministic yield carry (ADR-006).
    private final int[] progress = new int[PROCESS_SLOTS];
    private final int[] columnTime = new int[PROCESS_SLOTS];
    private final int[] yieldQuarters = new int[PROCESS_SLOTS];
    /** Current demand in E/t, all columns summed; 0 while idle. */
    private int currentDrawPerTick;
    /** E actually consumed on the last work tick (0 = idle or starved). */
    private int consumedLastTick;
    /** Some column could not pay its tick — frozen for lack of energy. */
    private boolean starvedLastTick;
    /** Input distribution: false = fill first (default), true = balance. */
    private boolean inputBalance;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress[0];
                case 1 -> columnTime[0];
                case 2 -> energy.getStored() & 0xFFFF;
                case 3 -> (energy.getStored() >> 16) & 0xFFFF;
                case 10 -> networkKoiosLevel();
                // 11..15: machine pos for the hologram view (x/z as low/high
                // short pairs, y fits a short on its own).
                case 11 -> worldPosition.getX() & 0xFFFF;
                case 12 -> (worldPosition.getX() >> 16) & 0xFFFF;
                case 13 -> worldPosition.getZ() & 0xFFFF;
                case 14 -> (worldPosition.getZ() >> 16) & 0xFFFF;
                case 15 -> worldPosition.getY();
                case 16 -> currentDrawPerTick;
                case 17 -> starvedLastTick ? 1 : 0;
                // 18..19: tier plumbing (hardware tier, live effective tier).
                case 18 -> machineTier;
                case 19 -> MachineTiers.effectiveTier(
                        NetworkManager.networkOf(CrusherBlockEntity.this), machineTier);
                // 20..21: second process column (progress, time).
                case 20 -> progress[1];
                case 21 -> columnTime[1];
                case 22 -> inputBalance ? 1 : 0;
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
            return CrusherMenu.DATA_COUNT;
        }
    };

    // Directions this machine supports, for the one-click presets.
    private static final short PRESET_IN_BITS = (short) (FaceConfig.flowBits(Flow.ENERGY, false)
            | FaceConfig.flowBits(Flow.ITEM, false));
    private static final short PRESET_OUT_BITS = (short) FaceConfig.flowBits(Flow.ITEM, true);
    /** Single source for the GUI hologram columns and the in-world overlay. */
    public static final short SUPPORTED_FACE_BITS = (short) (PRESET_IN_BITS | PRESET_OUT_BITS);

    /**
     * Face-config edits arrive through the vanilla menu button channel
     * (no custom packets): id = face*16 + sub, sub 15 = toggle mode,
     * sub 9..12 = presets (block / input only / output only / allow all),
     * sub 0..7 = flip flow bit (flow = sub/2, out = sub%2 == 1).
     */
    public boolean handleFaceButton(int id) {
        if (id == io.lakio.krios.industry.machine.MachineMenu.BUTTON_TOGGLE_DISTRIBUTION) {
            inputBalance = !inputBalance;
            setChanged();
            return true;
        }
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
                // Seed with the vanilla-equivalent contract so entering
                // manual mode alone changes nothing.
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
        // A manual item face may just have appeared: enter the wake chain.
        ItemFlow.scheduleWake(level, worldPosition, faceConfig);
        return true;
    }

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUSHER.get(), pos, state);
        machineTier = state.getBlock() instanceof TieredMachine tiered
                ? tiered.machineTier() : 1;
        // Hardware buffers scale with the machine tier.
        energy = new EnergyBuffer(CAPACITY * machineTier, 0);
    }

    public int machineTier() {
        return machineTier;
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

    // --- Flow ports: energy sink on every face; items stay out of
    // auto-config (no sensible complementary pair yet) ---

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

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrusherBlockEntity be) {
        // Online = member of a valid network (one formed core). Offline —
        // orphan or faulted component — falls back to vanilla parity:
        // furnace-grade time, half yield, modules inert (design).
        Network network = NetworkManager.networkOf(be);
        boolean online = network != null && network.isValid();
        // Online, producers push fair-shared; the pool is only PULLED on a
        // deficit, bounded to one tick's draw (an unbounded pull once let the
        // first-ticking machine tank the whole generator stock). The pull
        // doubles as the wake signal: draining a full idle generator relights
        // it (extractNetworkEnergy -> tryResume). currentDrawPerTick is last
        // tick's demand — the first tick after a wake freezes once, then
        // the pull catches up.
        if (!online) {
            be.pullEnergyFromContacts(level, pos);
        } else if (be.currentDrawPerTick > be.energy.getStored()) {
            int pulled = network.requestEnergy(
                    be.currentDrawPerTick - be.energy.getStored(), be);
            if (pulled > 0) {
                be.energy.insert(pulled);
            }
        }
        // Hopper-like flow: every output column streams into adjacent machines.
        for (int column = 0; column < PROCESS_SLOTS; column++) {
            ItemFlow.pushOutput(level, pos, be, OUTPUT_FIRST + column, be.faceConfig);
        }

        // Parallel processing (design 2026-09-30): the effective tier gates
        // how many columns RUN; each active column is an independent op at
        // baseline pace (tiers bring columns, modules bring speed). Energy
        // per op is unchanged — the draw sums over active columns.
        int effectiveTier = MachineTiers.effectiveTier(network, be.machineTier);
        int activeColumns = Math.min(PROCESS_SLOTS, MachineTiers.processSlots(effectiveTier));
        int speedPoints = online ? be.modulePoints(ModuleType.SPEED) : 0;
        int efficiencyPoints = online ? be.modulePoints(ModuleType.EFFICIENCY) : 0;
        int yieldPoints = online ? be.modulePoints(ModuleType.YIELD) : 0;

        int draw = 0;
        int consumed = 0;
        boolean anyWorking = false;
        boolean starvedNow = false;
        for (int column = 0; column < PROCESS_SLOTS; column++) {
            ItemStack input = be.items.get(INPUT_FIRST + column);
            RecipeHolder<CrushingRecipe> holder = column >= activeColumns || input.isEmpty() ? null
                    : be.quickCheck.getRecipeFor(new SingleRecipeInput(input), level).orElse(null);
            if (holder == null
                    || !be.canOutput(column, holder.value().result(), online, yieldPoints, effectiveTier)) {
                be.progress[column] = 0;
                be.columnTime[column] = 0;
                continue;
            }
            anyWorking = true;
            CrushingRecipe recipe = holder.value();
            // Speed: time x2/(2+points), shared by all columns.
            be.columnTime[column] = online
                    ? Math.max(1, recipe.time() * 2 / (2 + speedPoints))
                    : Math.max(recipe.time(), KoiosBalance.DISCONNECTED_TIME_TICKS);
            int energyPerOp = KoiosBalance.scaledEnergyCost(level,
                    recipe.energy() * KoiosBalance.efficiencyPermille(efficiencyPoints) / 1000);
            int energyPerTick = Math.max(1, energyPerOp / be.columnTime[column]);
            draw += energyPerTick;
            if (be.energy.consume(energyPerTick)) {
                consumed += energyPerTick;
                if (++be.progress[column] >= be.columnTime[column]) {
                    be.craft(column, holder, online, yieldPoints, effectiveTier);
                    be.progress[column] = 0;
                }
            } else {
                // No energy: this column stays frozen and waits — the next
                // push may refill us.
                starvedNow = true;
            }
        }
        be.currentDrawPerTick = draw;
        be.consumedLastTick = consumed;
        if (!anyWorking) {
            be.stopWork(level, pos, state);
            return;
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

    /** Point total on one axis (points model: Mk I = 1, Mk II = 2). */
    private int modulePoints(ModuleType type) {
        int points = 0;
        for (int slot = MODULE_SLOT_FIRST; slot <= MODULE_SLOT_LAST; slot++) {
            if (items.get(slot).getItem() instanceof ModuleItem module && module.getType() == type) {
                points += module.points();
            }
        }
        return points;
    }

    /** Bay slots this machine's tier activates (2 per tier). */
    public int activeModuleSlots() {
        return Math.min(MODULE_SLOT_COUNT, 2 * machineTier);
    }

    /**
     * Offline contact transfer: an adjacent generator still feeds a touching
     * consumer at vanilla parity (design: "feeds any adjacent consumer").
     * Face contracts apply on both sides; virgin faces keep the old behavior.
     */
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

    /**
     * Quarter-items produced per operation: x2 recipes fall back to 1:1
     * offline. The tier yield ladder (x2/x3/x3/x4 base, design 2026-09-30)
     * multiplies on top through the same accumulator.
     */
    private static int craftQuarters(ItemStack result, boolean online, int yieldPoints, int effectiveTier) {
        return online
                ? result.getCount() * (4 + yieldPoints)
                        * MachineTiers.yieldNum(effectiveTier) / 2
                : Math.max(1, result.getCount() / 2) * 4;
    }

    private boolean canOutput(int column, ItemStack result, boolean online, int yieldPoints,
            int effectiveTier) {
        int prospective = (yieldQuarters[column]
                + craftQuarters(result, online, yieldPoints, effectiveTier)) / 4;
        ItemStack current = items.get(OUTPUT_FIRST + column);
        if (current.isEmpty()) {
            return prospective <= result.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(current, result)
                && current.getCount() + prospective <= current.getMaxStackSize();
    }

    /**
     * Evolving yield multiplier (design): +25% per yield module on top of the
     * recipe result (x2 recipe -> x2.5 -> x3), carried in quarter-items.
     */
    private void craft(int column, RecipeHolder<CrushingRecipe> holder, boolean online,
            int yieldPoints, int effectiveTier) {
        ItemStack result = holder.value().result();
        yieldQuarters[column] += craftQuarters(result, online, yieldPoints, effectiveTier);
        int crafted = yieldQuarters[column] / 4;
        yieldQuarters[column] %= 4;
        items.get(INPUT_FIRST + column).shrink(1);
        ItemStack current = items.get(OUTPUT_FIRST + column);
        if (current.isEmpty()) {
            ItemStack out = result.copy();
            out.setCount(crafted);
            items.set(OUTPUT_FIRST + column, out);
        } else {
            current.grow(crafted);
        }
        Network network = NetworkManager.networkOf(this);
        if (network != null && network.controller() instanceof KoiosCoreBlockEntity core) {
            core.reportOperations(holder.id().toString(), 1, KoiosBalance.CRUSH_BASE_XP);
        }
    }

    private void stopWork(Level level, BlockPos pos, BlockState state) {
        java.util.Arrays.fill(progress, 0);
        java.util.Arrays.fill(columnTime, 0);
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

    /** Called when input lands while the crusher is off. */
    private void tryWake() {
        if (level == null || level.isClientSide
                || getBlockState().getValue(BlockStateProperties.LIT)) {
            return;
        }
        for (int column = 0; column < PROCESS_SLOTS; column++) {
            if (!items.get(INPUT_FIRST + column).isEmpty()) {
                level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, true), 3);
                return;
            }
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
            for (int column = 0; column < PROCESS_SLOTS; column++) {
                ItemFlow.pushOutput(level, worldPosition, this, OUTPUT_FIRST + column, faceConfig);
            }
            tryWake();
            ItemFlow.scheduleWake(level, worldPosition, faceConfig);
        }
    }

    /**
     * Scheduled wake (ADR-017): service the manual item faces — push
     * leftover outputs, pull one batch — then keep the chain alive.
     */
    public void scheduledWake() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int column = 0; column < PROCESS_SLOTS; column++) {
            ItemFlow.pushOutput(level, worldPosition, this, OUTPUT_FIRST + column, faceConfig);
        }
        ItemFlow.pullIntoMachine(level, worldPosition, this, faceConfig, pullBatch());
        ItemFlow.scheduleWake(level, worldPosition, faceConfig);
    }

    /** Pull batch from passive containers: base x effective-tier factor. */
    private int pullBatch() {
        return KoiosBalance.ITEM_PULL_BATCH_BASE * MachineTiers.speedFactor(
                MachineTiers.effectiveTier(NetworkManager.networkOf(this), machineTier));
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

    // --- WorldlyContainer (hoppers: input top/sides, output bottom). A
    // MANUAL face config is a complete contract (design): it also gates
    // third-party item access; virgin and auto-configured faces keep the
    // vanilla behavior. ---

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

    /** Physical columns this machine's tier provides (fillable slots). */
    private int activePhysicalColumns() {
        return Math.min(PROCESS_SLOTS, MachineTiers.processSlots(machineTier));
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        // Automated insertion (hoppers, machine flow) only takes what this
        // machine can actually process — players stay free in the GUI — and
        // only into the column the distribution mode elects.
        return slot >= INPUT_FIRST && slot < INPUT_FIRST + PROCESS_SLOTS
                && allowsItem(side, false) && isProcessable(stack)
                && slot == io.lakio.krios.industry.machine.InputDistribution.chooseColumn(
                        this, INPUT_FIRST, activePhysicalColumns(), stack, inputBalance);
    }

    private boolean isProcessable(ItemStack stack) {
        return level != null
                && quickCheck.getRecipeFor(new SingleRecipeInput(stack), level).isPresent();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= OUTPUT_FIRST && slot < OUTPUT_FIRST + PROCESS_SLOTS && allowsItem(side, true);
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
        // Freeing the output slot can unblock a stalled craft.
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
        // Any slot change may unblock the machine: new input, module swap
        // (totalTime/energy/yield change), or an emptied output.
        tryWake();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= INPUT_FIRST && slot < INPUT_FIRST + PROCESS_SLOTS;
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
        tag.putIntArray("Progress", progress.clone());
        tag.putIntArray("YieldQuarters", yieldQuarters.clone());
        tag.putBoolean("InputBalance", inputBalance);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy.load(tag);
        faceConfig.load(tag);
        // Pre-parallel saves stored plain ints here: getIntArray answers
        // empty and both arrays stay zeroed — a mid-craft op restarts.
        loadColumns(tag.getIntArray("Progress"), progress);
        loadColumns(tag.getIntArray("YieldQuarters"), yieldQuarters);
        inputBalance = tag.getBoolean("InputBalance");
    }

    private static void loadColumns(int[] saved, int[] target) {
        java.util.Arrays.fill(target, 0);
        System.arraycopy(saved, 0, target, 0, Math.min(saved.length, target.length));
    }
}
