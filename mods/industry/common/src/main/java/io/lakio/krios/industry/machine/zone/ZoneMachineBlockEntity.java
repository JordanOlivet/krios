package io.lakio.krios.industry.machine.zone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.machine.ConfigurableFaces;
import io.lakio.krios.industry.machine.EnergyStats;
import io.lakio.krios.industry.machine.ItemFlow;
import io.lakio.krios.industry.machine.MachineTiers;
import io.lakio.krios.industry.machine.TieredMachine;
import io.lakio.krios.industry.machine.ZoneGeometry;
import io.lakio.krios.industry.machine.module.ModuleItem;
import io.lakio.krios.industry.machine.module.ModuleType;
import io.lakio.krios.industry.machine.reactor.ThermalReactorBlockEntity;
import io.lakio.krios.industry.network.EnergySink;
import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.Flow;
import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkManager;
import io.lakio.krios.industry.network.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Shared core of the zone machines (extracted with the Cyclic Forester,
 * the second client — design 2026-10-08). Everything that is not "what to
 * do with one cell" lives here:
 *
 * <ul>
 * <li>the zone: behind the screen, edge by effective tier, -/+ one ring,
 * synced to clients for the hologram;</li>
 * <li>the ADR-022 wake chain: scheduled ticks only, 10 ticks after a wake
 * that acted, 100 otherwise; a full buffer is the one true stop;</li>
 * <li>the plantables-first routing into 3 plant slots, then the 9-slot
 * buffer, with the warned voiding toggle;</li>
 * <li>energy (pull before AND after the work), faces, modules, Speed
 * modules as forced random ticks, KOIOS reporting, persistence.</li>
 * </ul>
 *
 * Subclasses implement {@link #workCell} (and optionally
 * {@link #workQueued} for multi-wake jobs such as progressive felling).
 */
public abstract class ZoneMachineBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, NetworkNode, EnergySink, ConfigurableFaces, EnergyStats {

    // Layout: plantables (seeds, saplings), output buffer, modules.
    public static final int PLANT_FIRST = 0;
    public static final int PLANT_SLOTS = 3;
    public static final int BUFFER_FIRST = PLANT_FIRST + PLANT_SLOTS;
    public static final int BUFFER_SLOTS = 9;
    public static final int MODULE_SLOT_FIRST = BUFFER_FIRST + BUFFER_SLOTS;
    /** Physical bay ceiling; the machine tier gates the ACTIVE slots (2 per tier). */
    public static final int MODULE_SLOT_COUNT = 4;
    public static final int MODULE_SLOT_LAST = MODULE_SLOT_FIRST + MODULE_SLOT_COUNT - 1;
    public static final int INVENTORY_SIZE = MODULE_SLOT_LAST + 1;

    /** Same buffer as the grinder and the forge (8k per tier). */
    public static final int CAPACITY = 8_000;
    /**
     * Pull rate cap, per elapsed tick — the grinder's cadence, spent in
     * bursts since these machines only act on their wakes.
     */
    public static final int PULL_PER_TICK = 400;

    /** Vanilla menu-button ids (faces use 0..95, distribution 100). */
    public static final int BUTTON_ZONE_SHRINK = 101;
    public static final int BUTTON_ZONE_GROW = 102;
    public static final int BUTTON_TOGGLE_VOID = 103;

    // Terminal state line (synced code).
    public static final int STATE_WAITING = 0;
    public static final int STATE_WORKING = 1;
    public static final int STATE_BUFFER_FULL = 2;
    public static final int STATE_NO_PLANTS = 3;
    public static final int STATE_EMPTY_ZONE = 4;

    /** Elapsed ticks credited to forced growth are capped (long gaps, reloads). */
    private static final int MAX_ELAPSED_TICKS = 200;
    /**
     * Vanilla random tick odds per block per game tick are 3/4096 (default
     * randomTickSpeed); Speed modules add pts/2 of that rate, the same
     * x(2+pts)/2 pace as every other machine. Fixed-point carry: 8192.
     */
    private static final int GROWTH_ODDS_NUM = 3;
    private static final int GROWTH_ODDS_DEN = 8192;

    private static final int[] PLANT_SLOT_IDS = {0, 1, 2};
    private static final int[] BUFFER_SLOT_IDS = {3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int[] NO_SLOTS = {};

    /** Outcome of one unit of work. */
    protected enum CellResult { SKIP, ACTED, BLOCKED, STARVED }

    /**
     * Per-wake work context: module effects, the action budget and the
     * operations to report to KOIOS (key -> count).
     */
    protected static final class Work {
        public final int efficiencyPermille;
        public final int yieldPoints;
        public final int effectiveTier;
        public final int budget;
        public int actions;
        public boolean blocked;
        public boolean starved;
        @Nullable
        private Map<String, Integer> operations;

        Work(int efficiencyPermille, int yieldPoints, int effectiveTier, int budget) {
            this.efficiencyPermille = efficiencyPermille;
            this.yieldPoints = yieldPoints;
            this.effectiveTier = effectiveTier;
            this.budget = budget;
        }

        public boolean stopped() {
            return blocked || starved;
        }

        /** One completed operation for KOIOS (diversity rides the key). */
        public void report(String key) {
            if (operations == null) {
                operations = new HashMap<>();
            }
            operations.merge(key, 1, Integer::sum);
        }
    }

    private final int machineTier;
    protected final EnergyBuffer energy;
    protected final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final FaceConfig faceConfig = new FaceConfig();

    /** Plantable surplus is destroyed instead of buffered (off by default). */
    private boolean voidSurplus;
    /** Configured zone edge; 0 = the tier maximum (follows upgrades). */
    private int zoneSetting;
    /** Edge last shipped to clients (hologram); also the scan's edge. */
    private int syncedEdge;
    /** Next cell to scan; a pass sweeps the zone row by row. */
    private int cursor;
    /** Yield modules' quarter carry (ADR-006), shared by the subclass. */
    protected int yieldQuarters;
    private long growthCarry;
    private long lastWakeTime;
    /** Suppresses kicks fired by our own setItem calls during a wake. */
    private boolean inWake;

    // Pass bookkeeping, published when a full pass completes.
    private int passWorkable;
    private boolean passNoPlants;
    /** Workable cells found by the last full pass; -1 until one completes. */
    private int workableCells = -1;
    private boolean lastPassNoPlants;

    private int stateCode = STATE_WAITING;
    /** Average draw over the last wake interval, E/t. */
    private int drawPerTick;
    private boolean starved;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> currentEdge();
                case 1 -> maxEdge();
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
                case 16 -> drawPerTick;
                case 17 -> starved ? 1 : 0;
                case 18 -> machineTier;
                case 19 -> effectiveTier();
                case 20 -> workableCells;
                case 21 -> stateCode;
                case 22 -> voidSurplus ? 1 : 0;
                // 4..9: raw face-config shorts (11 bits, short-safe).
                default -> index >= 4 && index < 10
                        ? faceConfig.bits(Direction.from3DDataValue(index - 4)) : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; menus never write back.
        }

        @Override
        public int getCount() {
            return ZoneMachineMenu.DATA_COUNT;
        }
    };

    // Plantables come in, harvest goes out — same contract shape as the grinder.
    private static final short PRESET_IN_BITS = (short) (FaceConfig.flowBits(Flow.ENERGY, false)
            | FaceConfig.flowBits(Flow.ITEM, false));
    private static final short PRESET_OUT_BITS = (short) FaceConfig.flowBits(Flow.ITEM, true);
    /** Single source for the GUI hologram columns and the in-world overlay. */
    public static final short SUPPORTED_FACE_BITS = (short) (PRESET_IN_BITS | PRESET_OUT_BITS);

    protected ZoneMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        machineTier = state.getBlock() instanceof TieredMachine tiered ? tiered.machineTier() : 1;
        energy = new EnergyBuffer(CAPACITY * machineTier, 0);
    }

    public int machineTier() {
        return machineTier;
    }

    // --- Subclass contract ---

    /** The block state this item plants, or null when it is not a plantable we handle. */
    @Nullable
    protected abstract BlockState plantableState(ItemStack stack);

    /**
     * One zone cell at the machine's level. Call {@link #markWorkable} for
     * cells the machine can work, {@link #markNoPlants} when a cell waits
     * for a plantable the slots lack.
     */
    protected abstract CellResult workCell(ServerLevel level, BlockPos pos, int row, int col, Work work);

    /** A block Speed modules should randomly tick (growing plant). */
    protected abstract boolean isGrowing(BlockState state);

    /** Base data XP per reported operation (reduced, self-sufficient class). */
    protected abstract int operationXp();

    /**
     * Multi-wake jobs, run BEFORE the zone scan (progressive felling).
     * Returns true while the job keeps the machine busy — the scan then
     * waits for the next wake.
     */
    protected boolean workQueued(ServerLevel level, Work work) {
        return false;
    }

    /** Zone edge or direction changed (server side). */
    protected void onZoneChanged() {
    }

    // --- Zone ---

    protected int effectiveTier() {
        return MachineTiers.effectiveTier(NetworkManager.networkOf(this), machineTier);
    }

    private int maxEdge() {
        return MachineTiers.zoneEdge(effectiveTier());
    }

    private int currentEdge() {
        int max = maxEdge();
        return zoneSetting <= 0 ? max : Math.min(zoneSetting, max);
    }

    /** Zone edge as last synced (client side: the hologram's source). */
    public int zoneEdgeForDisplay() {
        return syncedEdge;
    }

    /**
     * The zone lies BEHIND the screen (user call after the first play
     * test): the screen faces the player at placement, so the zone extends
     * where the player was looking — set up from the field's edge.
     */
    public Direction zoneDirection() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
    }

    private void syncEdgeIfChanged() {
        int edge = currentEdge();
        if (edge != syncedEdge) {
            syncedEdge = edge;
            cursor = 0;
            resetPass();
            onZoneChanged();
            setChanged();
            syncToClients();
        }
    }

    /** One ring per click; reaching the maximum goes back to "follow the tier". */
    private void resizeZone(int delta) {
        int max = maxEdge();
        int edge = Math.max(1, Math.min(max, currentEdge() + delta));
        zoneSetting = edge >= max ? 0 : edge;
        syncEdgeIfChanged();
        kick();
    }

    // --- Wake chain (ADR-022) ---

    /** Makes sure a wake is pending; never shortens one already scheduled. */
    protected void kick() {
        if (level == null || level.isClientSide || inWake) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            level.scheduleTick(worldPosition, block, KoiosBalance.ZONE_ACTIVE_DELAY_TICKS);
        }
    }

    public void onNeighborChanged() {
        pushBuffer();
        kick();
    }

    public void scheduledWake(ServerLevel level) {
        inWake = true;
        try {
            int delay = wake(level);
            if (delay > 0) {
                level.scheduleTick(worldPosition, getBlockState().getBlock(), delay);
            } else {
                // Paused on a full buffer: only the manual item faces keep
                // a (slow) chain alive, to push the buffer out.
                ItemFlow.scheduleWake(level, worldPosition, faceConfig);
            }
        } finally {
            inWake = false;
        }
    }

    /** One wake; returns the next delay, or 0 to stop (buffer full). */
    private int wake(ServerLevel level) {
        long now = level.getGameTime();
        int elapsed = lastWakeTime <= 0 ? 1
                : (int) Math.max(1, Math.min(MAX_ELAPSED_TICKS, now - lastWakeTime));
        lastWakeTime = now;

        pushBuffer();
        ItemFlow.pullIntoMachine(level, worldPosition, this, faceConfig, pullBatch());

        Network network = NetworkManager.networkOf(this);
        boolean online = network != null && network.isValid();
        int pullBudget = PULL_PER_TICK * elapsed;
        pullBudget -= topUpEnergy(level, network, online, pullBudget);
        syncEdgeIfChanged();

        int effectiveTier = MachineTiers.effectiveTier(network, machineTier);
        int speedPoints = online ? modulePoints(ModuleType.SPEED) : 0;
        Work work = new Work(
                KoiosBalance.efficiencyPermille(online ? modulePoints(ModuleType.EFFICIENCY) : 0),
                online ? modulePoints(ModuleType.YIELD) : 0,
                effectiveTier,
                KoiosBalance.ZONE_ACTIONS_BASE * MachineTiers.speedFactor(effectiveTier));
        int storedBefore = energy.getStored();

        boolean busy = workQueued(level, work);
        if (!busy && !work.stopped()) {
            scan(level, work);
        }
        if (!work.starved && speedPoints > 0) {
            work.starved = forceGrowth(level, elapsed, speedPoints, work.efficiencyPermille);
        }
        if (work.operations != null) {
            reportOperations(network, work.operations);
        }

        int consumed = Math.max(0, storedBefore - energy.getStored());
        drawPerTick = consumed / elapsed;
        // Refill right after the work, same tick: with wakes up to 100
        // ticks apart, a buffer only topped up BEFORE working sat drained
        // until the next wake (seen in play as a sawtooth gauge).
        topUpEnergy(level, network, online, pullBudget);
        if (work.starved != starved) {
            starved = work.starved;
            notifyControllerEnergyState(network);
        }
        stateCode = work.starved ? STATE_WAITING
                : work.blocked ? STATE_BUFFER_FULL
                : work.actions > 0 ? STATE_WORKING
                : workableCells == 0 ? STATE_EMPTY_ZONE
                : lastPassNoPlants ? STATE_NO_PLANTS
                : STATE_WAITING;
        setLit(level, work.actions > 0);
        setChanged();
        if (work.blocked) {
            return 0;
        }
        // Starvation backs off like idleness: each wake still pulls (the
        // pull relights the producers), without polling every 10 ticks.
        return work.actions > 0 ? KoiosBalance.ZONE_ACTIVE_DELAY_TICKS
                : KoiosBalance.ZONE_IDLE_DELAY_TICKS;
    }

    /** Sweeps cells from the cursor until a full pass or the action budget. */
    private void scan(ServerLevel level, Work work) {
        int edge = syncedEdge;
        int cells = edge * edge;
        if (cursor >= cells) {
            cursor = 0;
            resetPass();
        }
        Direction direction = zoneDirection();
        for (int scanned = 0; scanned < cells && work.actions < work.budget; scanned++) {
            BlockPos pos = ZoneGeometry.cell(worldPosition, direction, edge, cursor);
            // Zones stop at unloaded chunks — never force-load (design).
            CellResult result = level.isLoaded(pos)
                    ? workCell(level, pos, cursor / edge, cursor % edge, work) : CellResult.SKIP;
            if (result == CellResult.BLOCKED) {
                work.blocked = true;
                return;
            }
            if (result == CellResult.STARVED) {
                work.starved = true;
                return;
            }
            if (result == CellResult.ACTED) {
                work.actions++;
            }
            if (++cursor >= cells) {
                endPass();
            }
            if (workQueuedPending()) {
                // A cell started a multi-wake job: it runs next wake.
                return;
            }
        }
    }

    /** True while a multi-wake job is pending (subclass state). */
    protected boolean workQueuedPending() {
        return false;
    }

    protected final void markWorkable() {
        passWorkable++;
    }

    protected final void markNoPlants() {
        passNoPlants = true;
    }

    private void resetPass() {
        passWorkable = 0;
        passNoPlants = false;
    }

    private void endPass() {
        workableCells = passWorkable;
        lastPassNoPlants = passNoPlants;
        cursor = 0;
        resetPass();
    }

    /**
     * Speed modules: extra random ticks on growing plants of the zone, the
     * vanilla rate scaled by pts/2. Returns true when energy ran out.
     */
    private boolean forceGrowth(ServerLevel level, int elapsed, int speedPoints, int efficiency) {
        int edge = syncedEdge;
        int cells = edge * edge;
        growthCarry += (long) cells * elapsed * speedPoints * GROWTH_ODDS_NUM;
        long due = growthCarry / GROWTH_ODDS_DEN;
        growthCarry -= due * GROWTH_ODDS_DEN;
        int ticks = (int) Math.min(due, KoiosBalance.ZONE_MAX_GROWTH_TICKS_PER_WAKE);
        int tickCost = cost(level, KoiosBalance.GROWTH_TICK_ENERGY, efficiency);
        Direction direction = zoneDirection();
        for (int i = 0; i < ticks; i++) {
            BlockPos pos = ZoneGeometry.cell(worldPosition, direction, edge, level.random.nextInt(cells));
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!isGrowing(state)) {
                continue;
            }
            if (!energy.consume(tickCost)) {
                return true;
            }
            state.randomTick(level, pos, level.random);
        }
        return false;
    }

    // --- Helpers for subclasses ---

    protected static int cost(Level level, int base, int efficiencyPermille) {
        return KoiosBalance.scaledEnergyCost(level, base * efficiencyPermille / 1000);
    }

    /** First plant slot whose plantable can stand at {@code pos}. */
    protected int plantSlotFor(Level level, BlockPos pos) {
        for (int slot = PLANT_FIRST; slot < PLANT_FIRST + PLANT_SLOTS; slot++) {
            BlockState plant = plantableState(items.get(slot));
            if (plant != null && plant.canSurvive(level, pos)) {
                return slot;
            }
        }
        return -1;
    }

    protected int plantSlotMatching(ItemStack plantable) {
        if (plantable.isEmpty()) {
            return -1;
        }
        for (int slot = PLANT_FIRST; slot < PLANT_FIRST + PLANT_SLOTS; slot++) {
            if (items.get(slot).is(plantable.getItem())) {
                return slot;
            }
        }
        return -1;
    }

    /** Plants the slot's item at {@code pos} (one item consumed). */
    protected void plant(ServerLevel level, BlockPos pos, int slot, SoundEvent sound) {
        ItemStack stack = items.get(slot);
        BlockState plant = plantableState(stack);
        stack.shrink(1);
        level.setBlock(pos, plant, 3);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    protected static boolean takeOne(List<ItemStack> drops, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return false;
        }
        for (ItemStack drop : drops) {
            if (drop.is(wanted.getItem()) && !drop.isEmpty()) {
                drop.shrink(1);
                return true;
            }
        }
        return false;
    }

    // --- Output buffer ---

    /**
     * Routes one batch of drops (user design 2026-10-08): everything
     * plantable tops the plant slots up first — no guessing what counts as
     * a seed — then the surplus goes to the buffer, or is destroyed when
     * the player enabled voiding (warned in the terminal: plantable
     * products are destroyed too). Everything else goes to the buffer.
     * {@code usedPlantSlot} (or -1) paid a replant from the slots.
     * Simulate first: false means the buffer cannot take the rest and
     * nothing may change.
     */
    protected boolean stow(List<ItemStack> drops, int usedPlantSlot, boolean simulate) {
        ItemStack[] plants = new ItemStack[PLANT_SLOTS];
        ItemStack[] buffer = new ItemStack[BUFFER_SLOTS];
        for (int i = 0; i < PLANT_SLOTS; i++) {
            ItemStack stack = items.get(PLANT_FIRST + i);
            plants[i] = simulate ? stack.copy() : stack;
        }
        for (int i = 0; i < BUFFER_SLOTS; i++) {
            ItemStack stack = items.get(BUFFER_FIRST + i);
            buffer[i] = simulate ? stack.copy() : stack;
        }
        if (usedPlantSlot >= 0) {
            plants[usedPlantSlot - PLANT_FIRST].shrink(1);
        }
        for (ItemStack drop : drops) {
            ItemStack rest = simulate ? drop.copy() : drop;
            if (plantableState(rest) != null) {
                rest = merge(plants, rest);
                if (voidSurplus) {
                    continue;
                }
            }
            if (!merge(buffer, rest).isEmpty()) {
                return false;
            }
        }
        if (!simulate) {
            for (int i = 0; i < PLANT_SLOTS; i++) {
                items.set(PLANT_FIRST + i, plants[i]);
            }
            for (int i = 0; i < BUFFER_SLOTS; i++) {
                items.set(BUFFER_FIRST + i, buffer[i]);
            }
        }
        return true;
    }

    /** Stacks onto matching slots first, then empty ones; returns the rest. */
    private static ItemStack merge(ItemStack[] slots, ItemStack stack) {
        for (ItemStack slot : slots) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                int move = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(move);
                stack.shrink(move);
            }
        }
        for (int i = 0; i < slots.length && !stack.isEmpty(); i++) {
            if (slots[i].isEmpty()) {
                slots[i] = stack.copy();
                stack.setCount(0);
            }
        }
        return stack;
    }

    private void pushBuffer() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int slot = BUFFER_FIRST; slot < BUFFER_FIRST + BUFFER_SLOTS; slot++) {
            ItemFlow.pushOutput(level, worldPosition, this, slot, faceConfig);
        }
    }

    /** Pull batch from passive containers: base x effective-tier factor. */
    private int pullBatch() {
        return KoiosBalance.ITEM_PULL_BATCH_BASE * MachineTiers.speedFactor(effectiveTier());
    }

    private void setLit(Level level, boolean lit) {
        BlockState state = getBlockState();
        if (state.getValue(BlockStateProperties.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, lit), 3);
        }
    }

    // --- KOIOS ---

    private void reportOperations(@Nullable Network network, Map<String, Integer> operations) {
        if (network != null && network.controller() instanceof KoiosCoreBlockEntity core) {
            operations.forEach((key, count) -> core.reportOperations(key, count, operationXp()));
        }
    }

    private void notifyControllerEnergyState(@Nullable Network network) {
        if (network != null && network.controller() instanceof KoiosCoreBlockEntity core) {
            core.energyStateChanged();
        }
    }

    /** KOIOS level of this machine's network controller; 0 while offline. */
    public int networkKoiosLevel() {
        Network network = NetworkManager.networkOf(this);
        return network != null && network.controller() instanceof KoiosCoreBlockEntity core
                ? core.getKoiosLevel() : 0;
    }

    // --- Modules ---

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

    // --- Energy ---

    /**
     * Buffer thermostat, consumer side: every wake tops the buffer up
     * (rate-capped), and the pull itself relights the producers it drains.
     * A paused-full reactor never pushes, so a starving machine must keep
     * pulling — starvation backs off, it never stops the chain.
     */
    private int topUpEnergy(Level level, @Nullable Network network, boolean online, int budget) {
        if (energy.isFull() || budget <= 0) {
            return 0;
        }
        int want = Math.min(budget, energy.getCapacity() - energy.getStored());
        if (online) {
            int pulled = network.requestEnergy(want, this);
            return pulled > 0 ? energy.insert(pulled) : 0;
        }
        int total = 0;
        // Offline contact transfer from an adjacent reactor (vanilla parity).
        for (Direction direction : Direction.values()) {
            if (faceConfig.isManual(direction) && !faceConfig.allows(direction, Flow.ENERGY, false)) {
                continue;
            }
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof ThermalReactorBlockEntity reactor
                    && reactor.allowsEnergyExtract(direction.getOpposite())) {
                int pulled = reactor.extractNetworkEnergy(
                        Math.min(want - total, energy.getCapacity() - energy.getStored()));
                if (pulled > 0) {
                    total += energy.insert(pulled);
                }
                if (energy.isFull() || total >= want) {
                    return total;
                }
            }
        }
        return total;
    }

    @Override
    public int insertNetworkEnergy(int max) {
        return energy.insert(Math.min(max, PULL_PER_TICK));
    }

    /** Working machines are served before idle buffers (surplus tier). */
    @Override
    public int sinkPriority() {
        return getBlockState().getValue(BlockStateProperties.LIT) ? 0 : 1;
    }

    @Override
    public int energyDrawPerTick() {
        return drawPerTick;
    }

    @Override
    public int energySupplyPerTick() {
        return 0;
    }

    @Override
    public int energyConsumedPerTick() {
        return drawPerTick;
    }

    @Override
    public boolean energyStarved() {
        return starved;
    }

    @Override
    public int energyStored() {
        return energy.getStored();
    }

    @Override
    public int energyCapacity() {
        return energy.getCapacity();
    }

    // --- Network node ---

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide) {
            NetworkManager.addNode(this);
            onZoneChanged();
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
        // Never setChanged() here: auto-config can run during chunk load.
        if (level != null) {
            NetworkManager.markUnsaved(level, worldPosition);
            syncToClients();
        }
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Menu buttons through the vanilla channel (no custom packets): zone
     * resize, voiding toggle, then the face-config protocol shared with
     * every machine.
     */
    public boolean handleButton(int id) {
        if (id == BUTTON_ZONE_SHRINK || id == BUTTON_ZONE_GROW) {
            resizeZone(id == BUTTON_ZONE_SHRINK ? -2 : 2);
            return true;
        }
        if (id == BUTTON_TOGGLE_VOID) {
            voidSurplus = !voidSurplus;
            setChanged();
            // A buffer jammed with plantables may resume once they stop landing.
            kick();
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
        syncToClients();
        // A manual item face may just have appeared: the chain must run.
        kick();
        return true;
    }

    // --- WorldlyContainer (hoppers: plantables top/sides, harvest bottom;
    // a MANUAL face config is a complete contract) ---

    private boolean allowsItem(Direction face, boolean out) {
        return !faceConfig.isManual(face) || faceConfig.allows(face, Flow.ITEM, out);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return allowsItem(side, true) ? BUFFER_SLOT_IDS : NO_SLOTS;
        }
        return allowsItem(side, false) ? PLANT_SLOT_IDS : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot >= PLANT_FIRST && slot < PLANT_FIRST + PLANT_SLOTS
                && (side == null || allowsItem(side, false)) && plantableState(stack) != null;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= BUFFER_FIRST && slot < BUFFER_FIRST + BUFFER_SLOTS && allowsItem(side, true);
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
        // Freeing the buffer resumes a paused machine.
        kick();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        kick();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
        // New plantables, a module swap or an emptied buffer slot.
        kick();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= PLANT_FIRST && slot < PLANT_FIRST + PLANT_SLOTS && plantableState(stack) != null;
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // --- Client sync: face contract + zone edge ride the BE update tag
    // (Calibrator overlay, zone hologram); the rest stays menu-synced. ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        faceConfig.save(tag);
        if (!tag.contains("Faces")) {
            // All-virgin still ships: an empty tag would be dropped.
            tag.putIntArray("Faces", new int[6]);
        }
        tag.putInt("ZoneEdge", syncedEdge);
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
        tag.putInt("ZoneSetting", zoneSetting);
        tag.putInt("ZoneEdge", syncedEdge);
        tag.putInt("Cursor", cursor);
        tag.putInt("YieldQuarters", yieldQuarters);
        tag.putLong("GrowthCarry", growthCarry);
        tag.putBoolean("VoidSurplusSeeds", voidSurplus);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy.load(tag);
        faceConfig.load(tag);
        zoneSetting = tag.getInt("ZoneSetting");
        syncedEdge = tag.getInt("ZoneEdge");
        cursor = tag.getInt("Cursor");
        yieldQuarters = tag.getInt("YieldQuarters");
        growthCarry = tag.getLong("GrowthCarry");
        voidSurplus = tag.getBoolean("VoidSurplusSeeds");
    }
}
