package io.lakio.krios.industry.machine.farmer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.machine.ConfigurableFaces;
import io.lakio.krios.industry.machine.EnergyStats;
import io.lakio.krios.industry.machine.HydrationZones;
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
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Chrono Farmer (design 2026-10-08): harvests mature crops in the zone
 * behind its screen, keeps the farmland moist, replants (seeds from the harvest first, the seed slots only
 * bootstrap the field), tills dirt it can plant right away. Paced by
 * scheduled ticks with idle backoff (ADR-022) — never a ticker. A full
 * output buffer is the one true stop (thermostat model for items); the
 * chain re-enters on extraction, insertion, neighbor change or a zone edit.
 */
public class ChronoFarmerBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, NetworkNode, EnergySink, ConfigurableFaces, EnergyStats {

    // Layout: seeds, output buffer, modules.
    public static final int SEED_FIRST = 0;
    public static final int SEED_SLOTS = 3;
    public static final int BUFFER_FIRST = SEED_FIRST + SEED_SLOTS;
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
     * bursts since this machine only acts on its wakes.
     */
    public static final int PULL_PER_TICK = 400;

    /** Vanilla menu-button ids (faces use 0..95, distribution 100). */
    public static final int BUTTON_ZONE_SHRINK = 101;
    public static final int BUTTON_ZONE_GROW = 102;
    public static final int BUTTON_TOGGLE_VOID_SEEDS = 103;

    // Terminal state line (synced code).
    public static final int STATE_WAITING = 0;
    public static final int STATE_WORKING = 1;
    public static final int STATE_BUFFER_FULL = 2;
    public static final int STATE_NO_SEEDS = 3;
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

    private static final int[] SEED_SLOT_IDS = {0, 1, 2};
    private static final int[] BUFFER_SLOT_IDS = {3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int[] NO_SLOTS = {};

    private enum CellResult { SKIP, ACTED, BLOCKED, STARVED }

    private final int machineTier;
    private final EnergyBuffer energy;
    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final FaceConfig faceConfig = new FaceConfig();

    /** Plantable surplus is destroyed instead of buffered (off by default). */
    private boolean voidSurplusSeeds;
    /** Configured zone edge; 0 = the tier maximum (follows upgrades). */
    private int zoneSetting;
    /** Edge last shipped to clients (hologram); also the menu's live value. */
    private int syncedEdge;
    /** Next cell to scan; a pass sweeps the zone row by row. */
    private int cursor;
    private int yieldQuarters;
    private long growthCarry;
    private long lastWakeTime;
    /** Suppresses kicks fired by our own setItem calls during a wake. */
    private boolean inWake;

    // Pass bookkeeping, published when a full pass completes.
    private int passWorkable;
    private boolean passNoSeeds;
    /** Workable cells found by the last full pass; -1 until one completes. */
    private int workableCells = -1;
    private boolean lastPassNoSeeds;

    private int stateCode = STATE_WAITING;
    /** Average draw over the last wake interval, E/t. */
    private int drawPerTick;
    private boolean starved;

    private final ContainerData dataAccess = new ContainerData() {
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
                case 22 -> voidSurplusSeeds ? 1 : 0;
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
            return ChronoFarmerMenu.DATA_COUNT;
        }
    };

    // Seeds come in, harvest goes out — same contract shape as the grinder.
    private static final short PRESET_IN_BITS = (short) (FaceConfig.flowBits(Flow.ENERGY, false)
            | FaceConfig.flowBits(Flow.ITEM, false));
    private static final short PRESET_OUT_BITS = (short) FaceConfig.flowBits(Flow.ITEM, true);
    /** Single source for the GUI hologram columns and the in-world overlay. */
    public static final short SUPPORTED_FACE_BITS = (short) (PRESET_IN_BITS | PRESET_OUT_BITS);

    public ChronoFarmerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHRONO_FARMER.get(), pos, state);
        machineTier = state.getBlock() instanceof TieredMachine tiered ? tiered.machineTier() : 1;
        energy = new EnergyBuffer(CAPACITY * machineTier, 0);
    }

    public int machineTier() {
        return machineTier;
    }

    // --- Crops ---

    /** Crops this machine handles in t1 scope: CropBlock family + nether wart. */
    static boolean isCrop(BlockState state) {
        Block block = state.getBlock();
        // Torchflower seeds grow INTO a flower block: not a harvest/replant crop.
        return block instanceof CropBlock && !(block instanceof TorchflowerCropBlock)
                || block instanceof NetherWartBlock;
    }

    static boolean isMature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
    }

    /** The crop state a seed item plants, or null when it is not a seed we handle. */
    @Nullable
    public static BlockState plantFor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item) {
            BlockState plant = item.getBlock().defaultBlockState();
            if (isCrop(plant)) {
                return plant;
            }
        }
        return null;
    }

    // --- Zone ---

    private int effectiveTier() {
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
            registerHydration();
            setChanged();
            syncToClients();
        }
    }

    /** The zone's soil layer counts as near water (HydrationZones, ADR-023). */
    private void registerHydration() {
        if (level == null || level.isClientSide || syncedEdge <= 0) {
            return;
        }
        BlockPos min = ZoneGeometry.min(worldPosition, zoneDirection(), syncedEdge).below();
        BlockPos max = ZoneGeometry.max(worldPosition, zoneDirection(), syncedEdge).below();
        HydrationZones.put(level, worldPosition, BoundingBox.fromCorners(min, max));
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
    private void kick() {
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
        int efficiency = KoiosBalance.efficiencyPermille(online ? modulePoints(ModuleType.EFFICIENCY) : 0);
        int yieldPoints = online ? modulePoints(ModuleType.YIELD) : 0;

        int edge = syncedEdge;
        int cells = edge * edge;
        if (cursor >= cells) {
            cursor = 0;
            resetPass();
        }
        Direction front = zoneDirection();
        int budget = KoiosBalance.ZONE_ACTIONS_BASE * MachineTiers.speedFactor(effectiveTier);
        int storedBefore = energy.getStored();
        Map<String, Integer> harvests = null;
        int actions = 0;
        boolean blocked = false;
        boolean starvedNow = false;

        for (int scanned = 0; scanned < cells && actions < budget; scanned++) {
            BlockPos cropPos = ZoneGeometry.cell(worldPosition, front, edge, cursor);
            BlockState crop = level.getBlockState(cropPos);
            CellResult result = workCell(level, cropPos, crop, efficiency, yieldPoints);
            if (result == CellResult.BLOCKED) {
                blocked = true;
                break;
            }
            if (result == CellResult.STARVED) {
                starvedNow = true;
                break;
            }
            if (result == CellResult.ACTED) {
                actions++;
                if (isCrop(crop) && isMature(crop)) {
                    if (harvests == null) {
                        harvests = new HashMap<>();
                    }
                    harvests.merge(BuiltInRegistries.BLOCK.getKey(crop.getBlock()).toString(), 1, Integer::sum);
                }
            }
            if (++cursor >= cells) {
                endPass();
            }
        }

        if (!starvedNow && speedPoints > 0) {
            starvedNow = forceGrowth(level, front, edge, elapsed, speedPoints, efficiency);
        }
        if (harvests != null) {
            reportHarvests(network, harvests);
        }

        int consumed = Math.max(0, storedBefore - energy.getStored());
        drawPerTick = consumed / elapsed;
        // Refill right after the work, same tick: with wakes up to 100
        // ticks apart, a buffer only topped up BEFORE working sat drained
        // until the next wake (seen in play as a sawtooth gauge).
        topUpEnergy(level, network, online, pullBudget);
        if (starvedNow != starved) {
            starved = starvedNow;
            notifyControllerEnergyState(network);
        }
        stateCode = starvedNow ? STATE_WAITING
                : blocked ? STATE_BUFFER_FULL
                : actions > 0 ? STATE_WORKING
                : workableCells == 0 ? STATE_EMPTY_ZONE
                : lastPassNoSeeds ? STATE_NO_SEEDS
                : STATE_WAITING;
        setLit(level, actions > 0);
        setChanged();
        if (blocked) {
            return 0;
        }
        // Starvation backs off like idleness: each wake still pulls (the
        // pull relights the producers), without polling every 10 ticks.
        return actions > 0 ? KoiosBalance.ZONE_ACTIVE_DELAY_TICKS
                : KoiosBalance.ZONE_IDLE_DELAY_TICKS;
    }

    private CellResult workCell(ServerLevel level, BlockPos cropPos, BlockState crop,
            int efficiency, int yieldPoints) {
        // Zones stop at unloaded chunks — never force-load (design).
        if (!level.isLoaded(cropPos)) {
            return CellResult.SKIP;
        }
        BlockPos soilPos = cropPos.below();
        BlockState soil = level.getBlockState(soilPos);
        hydrate(level, soilPos, soil, efficiency);
        if (isCrop(crop)) {
            passWorkable++;
            return isMature(crop) ? harvest(level, cropPos, crop, efficiency, yieldPoints)
                    : CellResult.SKIP;
        }
        if (!crop.isAir()) {
            return CellResult.SKIP;
        }
        if (soil.is(Blocks.FARMLAND) || soil.is(Blocks.SOUL_SAND)) {
            passWorkable++;
            int slot = seedSlotFor(level, cropPos);
            if (slot < 0) {
                passNoSeeds = true;
                return CellResult.SKIP;
            }
            if (!energy.consume(cost(level, KoiosBalance.PLANT_ENERGY, efficiency))) {
                return CellResult.STARVED;
            }
            plant(level, cropPos, slot);
            return CellResult.ACTED;
        }
        if (soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.DIRT_PATH)) {
            // Till only what we can plant right now: no point turning a
            // lawn into bare farmland the machine has nothing to sow on.
            int slot = farmlandSeedSlot(level, cropPos);
            if (slot < 0) {
                return CellResult.SKIP;
            }
            int tillCost = cost(level, KoiosBalance.TILL_ENERGY, efficiency);
            int plantCost = cost(level, KoiosBalance.PLANT_ENERGY, efficiency);
            if (energy.getStored() < tillCost + plantCost) {
                return CellResult.STARVED;
            }
            energy.consume(tillCost + plantCost);
            level.setBlock(soilPos, Blocks.FARMLAND.defaultBlockState()
                    .setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 3);
            level.playSound(null, soilPos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 0.5F, 1.0F);
            plant(level, cropPos, slot);
            return CellResult.ACTED;
        }
        return CellResult.SKIP;
    }

    /**
     * Keeps the zone's farmland at full moisture (user call after the first
     * play test): no water layout needed, and moist farmland is what makes
     * crops grow at full pace. The zone counts as near water (ADR-023), so
     * vanilla holds it at 7; this top-up only catches farmland that was
     * already dry when the zone formed. Not an "action" — it never holds
     * the fast pace on its own; skipped silently when energy is short.
     */
    private void hydrate(ServerLevel level, BlockPos soilPos, BlockState soil, int efficiency) {
        if (soil.is(Blocks.FARMLAND) && soil.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE
                && energy.consume(cost(level, KoiosBalance.HYDRATE_ENERGY, efficiency))) {
            level.setBlock(soilPos, soil.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 2);
        }
    }

    private CellResult harvest(ServerLevel level, BlockPos cropPos, BlockState crop,
            int efficiency, int yieldPoints) {
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(crop, level, cropPos, null));
        // Yield modules: +25% per point as whole extra harvests, carried in
        // quarters (ADR-006, deterministic) — committed only on success.
        int quarters = yieldQuarters + yieldPoints;
        for (int bonus = quarters / 4; bonus > 0; bonus--) {
            drops.addAll(Block.getDrops(crop, level, cropPos, null));
        }
        // Replant from the harvest first; the seed slots back it up.
        ItemStack seed = crop.getBlock().getCloneItemStack(level, cropPos, crop);
        boolean replant = takeOne(drops, seed);
        int seedSlot = -1;
        if (!replant) {
            seedSlot = seedSlotMatching(seed);
            replant = seedSlot >= 0;
        }
        if (!stow(drops, seedSlot, true)) {
            return CellResult.BLOCKED;
        }
        if (!energy.consume(cost(level, KoiosBalance.HARVEST_ENERGY, efficiency))) {
            return CellResult.STARVED;
        }
        yieldQuarters = quarters % 4;
        stow(drops, seedSlot, false);
        level.levelEvent(2001, cropPos, Block.getId(crop));
        level.setBlock(cropPos, replant ? crop.getBlock().defaultBlockState()
                : Blocks.AIR.defaultBlockState(), 3);
        return CellResult.ACTED;
    }

    private void plant(ServerLevel level, BlockPos cropPos, int slot) {
        ItemStack stack = items.get(slot);
        BlockState plant = plantFor(stack);
        stack.shrink(1);
        level.setBlock(cropPos, plant, 3);
        level.playSound(null, cropPos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    /**
     * Speed modules: extra random ticks on growing crops of the zone, the
     * vanilla growth rate scaled by pts/2. Returns true when energy ran out.
     */
    private boolean forceGrowth(ServerLevel level, Direction front, int edge, int elapsed,
            int speedPoints, int efficiency) {
        int cells = edge * edge;
        growthCarry += (long) cells * elapsed * speedPoints * GROWTH_ODDS_NUM;
        long due = growthCarry / GROWTH_ODDS_DEN;
        growthCarry -= due * GROWTH_ODDS_DEN;
        int ticks = (int) Math.min(due, KoiosBalance.ZONE_MAX_GROWTH_TICKS_PER_WAKE);
        int tickCost = cost(level, KoiosBalance.GROWTH_TICK_ENERGY, efficiency);
        for (int i = 0; i < ticks; i++) {
            BlockPos cropPos = ZoneGeometry.cell(worldPosition, front, edge, level.random.nextInt(cells));
            if (!level.isLoaded(cropPos)) {
                continue;
            }
            BlockState crop = level.getBlockState(cropPos);
            if (!isCrop(crop) || isMature(crop)) {
                continue;
            }
            if (!energy.consume(tickCost)) {
                return true;
            }
            crop.randomTick(level, cropPos, level.random);
        }
        return false;
    }

    private void resetPass() {
        passWorkable = 0;
        passNoSeeds = false;
    }

    private void endPass() {
        workableCells = passWorkable;
        lastPassNoSeeds = passNoSeeds;
        cursor = 0;
        resetPass();
    }

    private static int cost(Level level, int base, int efficiencyPermille) {
        return KoiosBalance.scaledEnergyCost(level, base * efficiencyPermille / 1000);
    }

    /** First seed slot whose crop can stand on this cell's soil. */
    private int seedSlotFor(Level level, BlockPos cropPos) {
        for (int slot = SEED_FIRST; slot < SEED_FIRST + SEED_SLOTS; slot++) {
            BlockState plant = plantFor(items.get(slot));
            if (plant != null && plant.canSurvive(level, cropPos)) {
                return slot;
            }
        }
        return -1;
    }

    /** First seed slot holding a farmland crop that would get enough light. */
    private int farmlandSeedSlot(Level level, BlockPos cropPos) {
        if (level.getRawBrightness(cropPos, 0) < 8 && !level.canSeeSky(cropPos)) {
            return -1;
        }
        for (int slot = SEED_FIRST; slot < SEED_FIRST + SEED_SLOTS; slot++) {
            BlockState plant = plantFor(items.get(slot));
            if (plant != null && plant.getBlock() instanceof CropBlock) {
                return slot;
            }
        }
        return -1;
    }

    private int seedSlotMatching(ItemStack seed) {
        if (seed.isEmpty()) {
            return -1;
        }
        for (int slot = SEED_FIRST; slot < SEED_FIRST + SEED_SLOTS; slot++) {
            if (items.get(slot).is(seed.getItem())) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean takeOne(List<ItemStack> drops, ItemStack seed) {
        if (seed.isEmpty()) {
            return false;
        }
        for (ItemStack drop : drops) {
            if (drop.is(seed.getItem()) && !drop.isEmpty()) {
                drop.shrink(1);
                return true;
            }
        }
        return false;
    }

    // --- Output buffer ---

    /**
     * Routes one harvest (user design 2026-10-08): everything plantable
     * tops the seed slots up first — carrots and potatoes included, no
     * guessing what counts as a seed — then the surplus goes to the buffer,
     * or is destroyed when the player enabled seed voiding (warned in the
     * terminal: plantable products are destroyed too). Everything else goes
     * to the buffer. {@code usedSeedSlot} (or -1) paid this harvest's
     * replant. Simulate first: false means the buffer cannot take the rest
     * and nothing may change.
     */
    private boolean stow(List<ItemStack> drops, int usedSeedSlot, boolean simulate) {
        ItemStack[] seeds = new ItemStack[SEED_SLOTS];
        ItemStack[] buffer = new ItemStack[BUFFER_SLOTS];
        for (int i = 0; i < SEED_SLOTS; i++) {
            ItemStack stack = items.get(SEED_FIRST + i);
            seeds[i] = simulate ? stack.copy() : stack;
        }
        for (int i = 0; i < BUFFER_SLOTS; i++) {
            ItemStack stack = items.get(BUFFER_FIRST + i);
            buffer[i] = simulate ? stack.copy() : stack;
        }
        if (usedSeedSlot >= 0) {
            seeds[usedSeedSlot - SEED_FIRST].shrink(1);
        }
        for (ItemStack drop : drops) {
            ItemStack rest = simulate ? drop.copy() : drop;
            if (plantFor(rest) != null) {
                rest = merge(seeds, rest);
                if (voidSurplusSeeds) {
                    continue;
                }
            }
            if (!merge(buffer, rest).isEmpty()) {
                return false;
            }
        }
        if (!simulate) {
            for (int i = 0; i < SEED_SLOTS; i++) {
                items.set(SEED_FIRST + i, seeds[i]);
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

    private void reportHarvests(@Nullable Network network, Map<String, Integer> harvests) {
        if (network != null && network.controller() instanceof KoiosCoreBlockEntity core) {
            harvests.forEach((crop, count) ->
                    core.reportOperations("harvest:" + crop, count, KoiosBalance.HARVEST_BASE_XP));
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

    // --- Energy ---

    /**
     * Buffer thermostat, consumer side: every wake tops the buffer up
     * (rate-capped), and the pull itself relights the producers it drains.
     * A paused-full reactor never pushes, so a starving farmer must keep
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

    /** Working farmers are served before idle buffers (surplus tier). */
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
            // A pure in-memory index entry: safe at chunk load, unlike
            // scheduling (ADR-017).
            registerHydration();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            NetworkManager.removeNode(this);
            HydrationZones.remove(level, worldPosition);
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
     * resize, then the face-config protocol shared with every machine.
     */
    public boolean handleButton(int id) {
        if (id == BUTTON_ZONE_SHRINK || id == BUTTON_ZONE_GROW) {
            resizeZone(id == BUTTON_ZONE_SHRINK ? -2 : 2);
            return true;
        }
        if (id == BUTTON_TOGGLE_VOID_SEEDS) {
            voidSurplusSeeds = !voidSurplusSeeds;
            setChanged();
            // A buffer jammed with seeds may resume once they stop landing.
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

    /** Bay slots this machine's tier activates (2 per tier). */
    public int activeModuleSlots() {
        return Math.min(MODULE_SLOT_COUNT, 2 * machineTier);
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.chrono_farmer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChronoFarmerMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- WorldlyContainer (hoppers: seeds top/sides, harvest bottom; a
    // MANUAL face config is a complete contract) ---

    private boolean allowsItem(Direction face, boolean out) {
        return !faceConfig.isManual(face) || faceConfig.allows(face, Flow.ITEM, out);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return allowsItem(side, true) ? BUFFER_SLOT_IDS : NO_SLOTS;
        }
        return allowsItem(side, false) ? SEED_SLOT_IDS : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot >= SEED_FIRST && slot < SEED_FIRST + SEED_SLOTS
                && (side == null || allowsItem(side, false)) && plantFor(stack) != null;
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
        // Freeing the buffer resumes a paused farmer.
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
        // New seeds, a module swap or an emptied buffer slot.
        kick();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= SEED_FIRST && slot < SEED_FIRST + SEED_SLOTS && plantFor(stack) != null;
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
        tag.putBoolean("VoidSurplusSeeds", voidSurplusSeeds);
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
        voidSurplusSeeds = tag.getBoolean("VoidSurplusSeeds");
    }
}
