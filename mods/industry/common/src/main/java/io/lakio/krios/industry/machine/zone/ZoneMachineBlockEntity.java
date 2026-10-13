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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Shared core of the zone machines (extracted with the Cyclic Forester,
 * split per ADR-025 with the Quantum Lure). Everything every zone machine
 * shares lives here:
 *
 * <ul>
 * <li>the zone: behind the screen, edge per machine (by effective tier
 * with -/+ one ring, or fixed), synced to clients for the hologram;</li>
 * <li>the ADR-022 wake chain: scheduled ticks only; the machine supplies
 * its work step, its next delay and its idle state; a full buffer is the
 * one true stop;</li>
 * <li>a generic input area (each machine declares its slot count, zero
 * allowed, and what they accept), the 9-slot output buffer, the module
 * bay (each machine declares the axes it takes);</li>
 * <li>energy (pull before AND after the work), faces, KOIOS reporting,
 * persistence.</li>
 * </ul>
 *
 * Plant slots, the cell scan and plantables-first routing live one layer
 * down, in {@link PlantingZoneBlockEntity}.
 */
public abstract class ZoneMachineBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, NetworkNode, EnergySink, ConfigurableFaces, EnergyStats {

    // Layout: inputs (per machine, from 0), output buffer (per machine,
    // 9 by default, zero allowed), modules.
    public static final int BUFFER_SLOTS = 9;
    /** Physical bay ceiling; the machine tier gates the ACTIVE slots (2 per tier). */
    public static final int MODULE_SLOT_COUNT = 4;

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

    // Terminal state line (synced code); machines add their own from 3.
    public static final int STATE_WAITING = 0;
    public static final int STATE_WORKING = 1;
    public static final int STATE_BUFFER_FULL = 2;

    /**
     * Synced data shared by every zone machine (indexes below); machine
     * data starts at this index (see {@link #machineData}).
     */
    public static final int BASE_DATA_COUNT = 21;

    /** Elapsed ticks credited per wake are capped (long gaps, reloads). */
    private static final int MAX_ELAPSED_TICKS = 200;

    private static final int[] NO_SLOTS = {};

    /** Outcome of one unit of work. */
    protected enum CellResult { SKIP, ACTED, BLOCKED, STARVED }

    /**
     * Per-wake work context: module effects, the action budget and the
     * operations to report to KOIOS (key -> count). Module points are
     * zero offline (modules inert, vanilla parity).
     */
    protected static final class Work {
        public final boolean online;
        public final int elapsed;
        public final int speedPoints;
        public final int efficiencyPermille;
        public final int yieldPoints;
        public final int effectiveTier;
        public final int budget;
        public int actions;
        public boolean blocked;
        public boolean starved;
        @Nullable
        private Map<String, Integer> operations;

        Work(boolean online, int elapsed, int speedPoints, int efficiencyPermille, int yieldPoints,
                int effectiveTier, int budget) {
            this.online = online;
            this.elapsed = elapsed;
            this.speedPoints = speedPoints;
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
    private final int inputSlots;
    private final int bufferSlots;
    protected final EnergyBuffer energy;
    protected final NonNullList<ItemStack> items;
    private final FaceConfig faceConfig = new FaceConfig();
    private final int[] inputSlotIds;
    private final int[] bufferSlotIds;

    /** Configured zone edge; 0 = the maximum (follows upgrades). */
    private int zoneSetting;
    /** Edge last shipped to clients (hologram); also the work's edge. */
    private int syncedEdge;
    /** Yield modules' quarter carry (ADR-006), shared by the subclass. */
    protected int yieldQuarters;
    private long lastWakeTime;
    /**
     * Game time of the next WORK wake. Wakes before it only push the
     * buffer out (user call after the Lure's play test: a non-empty buffer
     * keeps flowing to its targets between actions). 0 = work at the next
     * wake (modules changed).
     */
    private long nextWorkTime;
    /**
     * Stopped on a full buffer (ADR-022's one true stop): wakes only push,
     * until a push frees room or a kick (player, hopper, face edit) says
     * the state changed.
     */
    private boolean paused;
    /** Suppresses kicks fired by our own setItem calls during a wake. */
    private boolean inWake;

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
                case 20 -> stateCode;
                // 4..9: raw face-config shorts (11 bits, short-safe).
                default -> index >= 4 && index < 10
                        ? faceConfig.bits(Direction.from3DDataValue(index - 4))
                        : index >= BASE_DATA_COUNT ? machineData(index) : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; menus never write back.
        }

        @Override
        public int getCount() {
            return dataCount();
        }
    };

    // Inputs come in, products go out — same contract shape as the grinder.
    private static final short PRESET_IN_BITS = (short) (FaceConfig.flowBits(Flow.ENERGY, false)
            | FaceConfig.flowBits(Flow.ITEM, false));
    private static final short PRESET_OUT_BITS = (short) FaceConfig.flowBits(Flow.ITEM, true);
    /** Single source for the GUI hologram columns and the in-world overlay. */
    public static final short SUPPORTED_FACE_BITS = (short) (PRESET_IN_BITS | PRESET_OUT_BITS);

    protected ZoneMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int inputSlots) {
        this(type, pos, state, inputSlots, BUFFER_SLOTS);
    }

    protected ZoneMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            int inputSlots, int bufferSlots) {
        super(type, pos, state);
        machineTier = state.getBlock() instanceof TieredMachine tiered ? tiered.machineTier() : 1;
        energy = new EnergyBuffer(CAPACITY * machineTier, 0);
        this.inputSlots = inputSlots;
        this.bufferSlots = bufferSlots;
        items = NonNullList.withSize(inventorySize(inputSlots, bufferSlots), ItemStack.EMPTY);
        inputSlotIds = new int[inputSlots];
        for (int i = 0; i < inputSlots; i++) {
            inputSlotIds[i] = i;
        }
        bufferSlotIds = new int[bufferSlots];
        for (int i = 0; i < bufferSlots; i++) {
            bufferSlotIds[i] = inputSlots + i;
        }
    }

    /** Container size for a machine's layout (menus share it). */
    public static int inventorySize(int inputSlots, int bufferSlots) {
        return inputSlots + bufferSlots + MODULE_SLOT_COUNT;
    }

    public int machineTier() {
        return machineTier;
    }

    // --- Layout ---

    protected final int inputSlots() {
        return inputSlots;
    }

    protected final int bufferFirst() {
        return inputSlots;
    }

    protected final int moduleSlotFirst() {
        return inputSlots + bufferSlots;
    }

    protected final boolean isInputSlot(int slot) {
        return slot >= 0 && slot < inputSlots;
    }

    protected final boolean isBufferSlot(int slot) {
        return slot >= inputSlots && slot < inputSlots + bufferSlots;
    }

    // --- Subclass contract ---

    /**
     * One wake's work, after the energy top-up and before the reporting.
     * Count actions in {@code work.actions}, set {@code work.blocked} on a
     * full buffer and {@code work.starved} when energy ran out.
     */
    protected abstract void work(ServerLevel level, Work work);

    /** State code when the wake neither worked, blocked nor starved. */
    protected abstract int idleState();

    /** Base data XP per reported operation (reduced, self-sufficient class). */
    protected abstract int operationXp();

    /** What an input slot accepts (hoppers, the menu, shift-click). */
    protected boolean acceptsInput(ItemStack stack) {
        return false;
    }

    /** Delay before the next wake, unless the buffer is full. */
    protected int nextDelay(Work work) {
        // Starvation backs off like idleness: each wake still pulls (the
        // pull relights the producers), without polling every 10 ticks.
        return work.actions > 0 ? KoiosBalance.ZONE_ACTIVE_DELAY_TICKS : KoiosBalance.ZONE_IDLE_DELAY_TICKS;
    }

    /** LIT ("working", visual) after this wake. */
    protected boolean isWorking(Work work) {
        return work.actions > 0;
    }

    /** Synced data count: the base block plus the machine's own. */
    protected int dataCount() {
        return BASE_DATA_COUNT;
    }

    /** Machine-specific synced data, from {@link #BASE_DATA_COUNT}. */
    protected int machineData(int index) {
        return 0;
    }

    /** Machine-specific menu buttons (ids outside the shared channel). */
    protected boolean handleMachineButton(int id) {
        return false;
    }

    /**
     * The module bay changed (server side), just before the immediate wake:
     * machines with work in flight rescale it to the new modules.
     */
    protected void onModulesChanged() {
    }

    /** Zone edge or direction changed (server side; also on load). */
    protected void onZoneChanged() {
    }

    /** The synced edge just changed (server side, never on load). */
    protected void onEdgeChanged() {
    }

    // --- Zone ---

    protected int effectiveTier() {
        return MachineTiers.effectiveTier(NetworkManager.networkOf(this), machineTier);
    }

    /** Largest zone edge; the tier ladder by default. */
    protected int maxEdge() {
        return MachineTiers.zoneEdge(effectiveTier());
    }

    /** Whether the player may shrink the zone (-/+); fixed zones say no. */
    protected boolean zoneResizable() {
        return true;
    }

    private int currentEdge() {
        int max = maxEdge();
        return zoneSetting <= 0 || !zoneResizable() ? max : Math.min(zoneSetting, max);
    }

    /** Zone height in blocks from the machine's Y (the hologram draws it). */
    public int zoneHeight() {
        return 1;
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
            onEdgeChanged();
            onZoneChanged();
            setChanged();
            syncToClients();
        }
    }

    /** One ring per click; reaching the maximum goes back to "follow the tier". */
    private void resizeZone(int delta) {
        if (!zoneResizable()) {
            return;
        }
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
        paused = false;
        Block block = getBlockState().getBlock();
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            level.scheduleTick(worldPosition, block, KoiosBalance.ZONE_ACTIVE_DELAY_TICKS);
        }
    }

    public void onNeighborChanged() {
        pushBuffer();
        kick();
    }

    /**
     * Wakes the machine at the next tick, replacing a later pending wake
     * (vanilla keeps one scheduled tick per block: clear, then schedule).
     * Player-driven only — never called from inside a wake.
     */
    protected void wakeSoon() {
        if (level instanceof ServerLevel server && !inWake) {
            nextWorkTime = 0;
            paused = false;
            server.getBlockTicks().clearArea(new BoundingBox(worldPosition));
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    public void scheduledWake(ServerLevel level) {
        inWake = true;
        try {
            long now = level.getGameTime();
            if (paused) {
                if (!pushBuffer()) {
                    // Still jammed: only the manual item faces keep a
                    // (slow) chain alive, to push the buffer out.
                    ItemFlow.scheduleWake(level, worldPosition, faceConfig);
                    return;
                }
                paused = false;
            } else if (nextWorkTime > 0 && now < nextWorkTime) {
                pushWake(level, now);
                return;
            }
            int delay = wake(level);
            if (delay > 0) {
                nextWorkTime = now + delay;
                scheduleNext(level, now);
            } else {
                paused = true;
                nextWorkTime = 0;
                ItemFlow.scheduleWake(level, worldPosition, faceConfig);
            }
        } finally {
            inWake = false;
        }
    }

    /**
     * A wake between two work wakes: push the buffer only. While items
     * keep leaving, push again soon; once nothing moves (targets full or
     * none), fall back to the work cadence — a full chest notifies nobody
     * when it empties, the work wakes retry anyway.
     */
    private void pushWake(ServerLevel level, long now) {
        if (pushBuffer() && bufferHasItems()) {
            scheduleNext(level, now);
        } else {
            level.scheduleTick(worldPosition, getBlockState().getBlock(),
                    (int) Math.max(1, nextWorkTime - now));
        }
    }

    /** Next wake: the work wake, or sooner to push a non-empty buffer. */
    private void scheduleNext(ServerLevel level, long now) {
        long delay = Math.max(1, nextWorkTime - now);
        if (bufferHasItems()) {
            delay = Math.min(delay, KoiosBalance.ZONE_PUSH_DELAY_TICKS);
        }
        level.scheduleTick(worldPosition, getBlockState().getBlock(), (int) delay);
    }

    private boolean bufferHasItems() {
        for (int slot = bufferFirst(); slot < bufferFirst() + bufferSlots; slot++) {
            if (!items.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
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
        Work work = new Work(online, elapsed,
                online ? modulePoints(ModuleType.SPEED) : 0,
                KoiosBalance.efficiencyPermille(online ? modulePoints(ModuleType.EFFICIENCY) : 0),
                online ? modulePoints(ModuleType.YIELD) : 0,
                effectiveTier,
                KoiosBalance.ZONE_ACTIONS_BASE * MachineTiers.speedFactor(effectiveTier));
        int storedBefore = energy.getStored();

        work(level, work);
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
                : isWorking(work) ? STATE_WORKING
                : idleState();
        setLit(level, isWorking(work));
        setChanged();
        return work.blocked ? 0 : Math.max(1, nextDelay(work));
    }

    // --- Helpers for subclasses ---

    protected static int cost(Level level, int base, int efficiencyPermille) {
        return KoiosBalance.scaledEnergyCost(level, base * efficiencyPermille / 1000);
    }

    // --- Output buffer ---

    /**
     * Stows one batch of drops into the buffer. Simulate first: false
     * means the buffer cannot take everything and nothing may change.
     */
    protected boolean stow(List<ItemStack> drops, boolean simulate) {
        ItemStack[] buffer = bufferView(simulate);
        for (ItemStack drop : drops) {
            if (!merge(buffer, simulate ? drop.copy() : drop).isEmpty()) {
                return false;
            }
        }
        if (!simulate) {
            commitBuffer(buffer);
        }
        return true;
    }

    /** At least one empty buffer slot (drops of unknown size go in whole). */
    protected final boolean hasFreeBufferSlot() {
        for (int slot = bufferFirst(); slot < bufferFirst() + bufferSlots; slot++) {
            if (items.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The buffer's stacks, copied when simulating. */
    protected final ItemStack[] bufferView(boolean copy) {
        ItemStack[] buffer = new ItemStack[bufferSlots];
        for (int i = 0; i < bufferSlots; i++) {
            ItemStack stack = items.get(bufferFirst() + i);
            buffer[i] = copy ? stack.copy() : stack;
        }
        return buffer;
    }

    protected final void commitBuffer(ItemStack[] buffer) {
        for (int i = 0; i < bufferSlots; i++) {
            items.set(bufferFirst() + i, buffer[i]);
        }
    }

    /** Stacks onto matching slots first, then empty ones; returns the rest. */
    protected static ItemStack merge(ItemStack[] slots, ItemStack stack) {
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

    /** Pushes every buffer slot out; true when anything left. */
    private boolean pushBuffer() {
        if (level == null || level.isClientSide) {
            return false;
        }
        int before = bufferCount();
        for (int slot = bufferFirst(); slot < bufferFirst() + bufferSlots; slot++) {
            ItemFlow.pushOutput(level, worldPosition, this, slot, faceConfig);
        }
        return bufferCount() < before;
    }

    private int bufferCount() {
        int count = 0;
        for (int slot = bufferFirst(); slot < bufferFirst() + bufferSlots; slot++) {
            count += items.get(slot).getCount();
        }
        return count;
    }

    private boolean isModuleSlot(int slot) {
        return slot >= moduleSlotFirst() && slot < moduleSlotFirst() + MODULE_SLOT_COUNT;
    }

    /** A module went in or out: its effect applies now, not at the next action. */
    private void modulesChanged(int slot) {
        if (isModuleSlot(slot) && level != null && !level.isClientSide && !inWake) {
            onModulesChanged();
            wakeSoon();
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

    /** On a valid network (modules are inert offline). */
    protected boolean isOnline() {
        Network network = NetworkManager.networkOf(this);
        return network != null && network.isValid();
    }

    /** Point total on one axis across the bay (raw: callers gate on online). */
    protected int modulePoints(ModuleType type) {
        int points = 0;
        for (int slot = moduleSlotFirst(); slot < moduleSlotFirst() + MODULE_SLOT_COUNT; slot++) {
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

    protected final void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Menu buttons through the vanilla channel (no custom packets): zone
     * resize, the machine's own buttons, then the face-config protocol
     * shared with every machine.
     */
    public boolean handleButton(int id) {
        if (id == BUTTON_ZONE_SHRINK || id == BUTTON_ZONE_GROW) {
            resizeZone(id == BUTTON_ZONE_SHRINK ? -2 : 2);
            return true;
        }
        if (handleMachineButton(id)) {
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

    // --- WorldlyContainer (hoppers: inputs top/sides, products bottom;
    // a MANUAL face config is a complete contract) ---

    private boolean allowsItem(Direction face, boolean out) {
        return !faceConfig.isManual(face) || faceConfig.allows(face, Flow.ITEM, out);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return allowsItem(side, true) ? bufferSlotIds : NO_SLOTS;
        }
        return allowsItem(side, false) ? inputSlotIds : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return isInputSlot(slot) && (side == null || allowsItem(side, false)) && acceptsInput(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return isBufferSlot(slot) && allowsItem(side, true);
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
        modulesChanged(slot);
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        kick();
        modulesChanged(slot);
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
        // New inputs, a module swap or an emptied buffer slot.
        kick();
        modulesChanged(slot);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isInputSlot(slot) && acceptsInput(stack);
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
        tag.putInt("YieldQuarters", yieldQuarters);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy.load(tag);
        faceConfig.load(tag);
        zoneSetting = tag.getInt("ZoneSetting");
        syncedEdge = tag.getInt("ZoneEdge");
        yieldQuarters = tag.getInt("YieldQuarters");
    }
}
