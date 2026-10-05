package io.lakio.krios.industry.koios;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.lakio.krios.industry.machine.EnergyReport;
import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkManager;
import io.lakio.krios.industry.network.NetworkNode;
import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.registry.ModBlocks;
import io.lakio.krios.industry.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class KoiosCoreBlockEntity extends BlockEntity implements MenuProvider, NetworkNode {

    // The 26 positions of the full 3x3x3 shell. The core is fully encased so
    // the shell faces can act as the AI's "screens" (head display).
    private static final Vec3i[] SHELL_OFFSETS = buildShellOffsets();

    // Block events driving the client-side head animations (vanilla blockEvent
    // channel — no custom packets, no per-tick sync).
    public static final int EVENT_PULSE = 1;
    public static final int EVENT_LEVEL_UP = 2;
    public static final int EVENT_BOOT = 3;
    /** Critical breakthrough paid: the big tier-up sequence (param = new tier). */
    public static final int EVENT_TIER_UP = 4;
    // Art direction: learning pulses aggregate at scale, max ~1/s. Ops
    // accumulate between pulses and ride the event param so the client can
    // read batch size (a busy factory keeps the activity LED lit longer).
    private static final int PULSE_MIN_INTERVAL_TICKS = 20;

    private int xp;
    /** Hardware tier of this core (1..MAX_TIER); rides KoiosData on drops. */
    private int tier = 1;
    private int koiosLevel = 1;
    /** Which face of the shell displays the AI's head; null = none yet. */
    @Nullable
    private Direction screenSide;
    private long lastPulseGameTime;
    /** Ops since the last pulse fired; visual only, never saved. */
    private int pendingPulseOps;
    /**
     * Derived from the graph, never saved: true while this core shares a
     * component with another active controller (one core per network). The
     * client renders TV static + a red error panel over the face.
     */
    private boolean networkFault;

    // Client-side animation clocks, set by triggerEvent (gameTime values).
    private long clientBootStart;
    private long clientPulseStart;
    private long clientLevelUpStart;
    private long clientTierUpStart;
    /** Batch size behind the running pulse (event param, min 1). */
    private int clientPulseOps = 1;
    /** Diversity received via update tag (client); -1 = compute locally. */
    private int clientDiversity = -1;
    private int clientEnergyPermille = 1000;
    private boolean clientEnergyDeficit;

    // Sliding diversity window: ring buffer of the last N operation keys plus
    // per-key counts for O(1) share lookups (design: diminishing returns on
    // repetition, recovers as operations vary).
    private final String[] window = new String[KoiosBalance.WINDOW_SIZE];
    private int windowHead;
    private int windowFill;
    private final Map<String, Integer> windowCounts = new HashMap<>();
    /**
     * Codex badge state: the visible-entry signature the codex was last
     * opened at (see CodexPanel.visibleSignature). Written through the
     * menu's button channel; per-core by design (v0 — in multiplayer one
     * reader clears the dot for the structure, acceptable for now).
     */
    private int codexSeen;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> koiosLevel;
                case 1 -> isFormed() ? 1 : 0;
                case 2 -> diversityPermille();
                case 3 -> xp & 0xFFFF;
                case 4 -> (xp >> 16) & 0xFFFF;
                // 5..6: the raw numbers behind the diversity gauge (tooltip).
                case 5 -> windowCounts.size();
                case 6 -> windowFill;
                // 7..8: network energy health (KOIOS alerts).
                case 7 -> networkEnergyPermille();
                case 8 -> networkEnergyDeficit() ? 1 : 0;
                case 9 -> tier;
                // 10: codex badge — the visible-entry signature last seen.
                case 10 -> codexSeen;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; menus never write back.
        }

        @Override
        public int getCount() {
            return KoiosCoreMenu.DATA_COUNT;
        }
    };

    public KoiosCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KOIOS_CORE.get(), pos, state);
    }

    // --- Network node: the core is the network's controller ---

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

    @Override
    public boolean isController() {
        return true;
    }

    @Override
    public boolean isActiveController() {
        return isFormed();
    }

    @Override
    public void onNetworkChanged(Network network) {
        boolean fault = network.isFault();
        if (fault != networkFault) {
            networkFault = fault;
            syncToClients();
        }
    }

    public boolean isNetworkFault() {
        return networkFault;
    }

    // --- Learning ---

    /**
     * Data XP entry point, called by machines on completed operations. The
     * key identifies the operation for the diversity window (recipe id,
     * "burn:&lt;item&gt;", ...).
     */
    public void reportOperations(String key, int count, int baseXp) {
        if (!isFormed()) {
            return;
        }
        int cap = KoiosBalance.xpCap(tier);
        for (int i = 0; i < count && xp < cap; i++) {
            xp = Math.min(cap, xp + KoiosBalance.scaledOpXp(level, baseXp, diversityFactorPermille(key)));
            pushWindow(key);
        }
        int previousLevel = koiosLevel;
        koiosLevel = KoiosBalance.levelForXp(xp, tier);
        setChanged();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        pendingPulseOps += count;
        if (now - lastPulseGameTime >= PULSE_MIN_INTERVAL_TICKS) {
            lastPulseGameTime = now;
            // Capped: the client only needs a coarse busy read, and 255
            // keeps the param byte-safe on any packet encoding.
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_PULSE,
                    Math.min(pendingPulseOps, 255));
            pendingPulseOps = 0;
        }
        if (koiosLevel > previousLevel) {
            level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.3F);
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_LEVEL_UP, koiosLevel);
            syncToClients();
        }
    }

    public int getKoiosLevel() {
        return koiosLevel;
    }

    public int getXp() {
        return xp;
    }

    /**
     * Admin entry point (/koios command): sets data XP directly, bypassing
     * the diversity window. Lowering XP is allowed (re-testing gates).
     */
    public void setXpAdmin(int newXp) {
        int clamped = Math.max(0, Math.min(KoiosBalance.xpCap(tier), newXp));
        if (clamped == xp) {
            return;
        }
        int previousLevel = koiosLevel;
        xp = clamped;
        koiosLevel = KoiosBalance.levelForXp(xp, tier);
        setChanged();
        if (level != null && koiosLevel > previousLevel) {
            level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.3F);
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_LEVEL_UP, koiosLevel);
        }
        syncToClients();
    }

    /** Admin tier set (testing); recomputes the level, clamps XP down never. */
    public void setTierAdmin(int newTier) {
        tier = Math.max(1, Math.min(KoiosBalance.MAX_TIER, newTier));
        koiosLevel = KoiosBalance.levelForXp(xp, tier);
        setChanged();
        syncToClients();
    }

    public int getTier() {
        return tier;
    }

    /**
     * XP sits at the tier's critical threshold: the breakthrough is
     * payable — or announced as coming soon while the next tier's curve
     * does not exist yet.
     */
    public boolean isCriticalPending() {
        return isFormed() && tier < KoiosBalance.MAX_TIER && xp >= KoiosBalance.xpCap(tier);
    }

    /**
     * The critical breakthrough (design 2026-09-30): XP is a threshold —
     * never consumed — and the caller consumes the compute chip on true.
     * The new tier reopens the level curve (the paid critical level is
     * acquired on the spot) and fires the in-world tier-up sequence.
     */
    public boolean tryBreakthrough() {
        if (!isCriticalPending() || !KoiosBalance.hasTierCurve(tier + 1) || level == null) {
            return false;
        }
        tier++;
        koiosLevel = KoiosBalance.levelForXp(xp, tier);
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.1F);
        level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 0.9F);
        level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_TIER_UP, tier);
        syncToClients();
        return true;
    }

    /** The codex was opened at this visible-entry signature (badge reset). */
    public void codexOpened(int signature) {
        if (signature != codexSeen) {
            codexSeen = signature;
            setChanged();
        }
    }

    public boolean isFormed() {
        // Read the LIVE world state, never the BE's cached one. When the
        // core entity is born inside another block's place/remove cascade
        // (replacing a casing, /setblock, structure paste), the formation
        // setBlock nests inside the outer setBlockState, and vanilla then
        // finishes the outer frame by clobbering the cache with its stale
        // pre-formation state — a core that looks formed in the world but
        // answers false here silently invalidates the whole network (no
        // controller, no XP, no energy pool, machines fall back offline).
        BlockState state = level != null
                ? level.getBlockState(worldPosition) : getBlockState();
        return state.hasProperty(KoiosCoreBlock.FORMED)
                && state.getValue(KoiosCoreBlock.FORMED);
    }

    private int diversityFactorPermille(String key) {
        if (windowFill == 0) {
            return 1000;
        }
        int share = windowCounts.getOrDefault(key, 0) * 1000 / windowFill;
        int knee = KoiosBalance.DIVERSITY_KNEE_PERMILLE;
        int floor = KoiosBalance.DIVERSITY_FLOOR_PERMILLE;
        if (share <= knee) {
            return 1000;
        }
        return Math.max(floor, 1000 - (share - knee) * (1000 - floor) / (1000 - knee));
    }

    /** Distinct operations over window fill, per-mille — the GUI's diversity gauge. */
    public int diversityPermille() {
        return windowFill == 0 ? 1000 : windowCounts.size() * 1000 / windowFill;
    }

    /** Renderer-side diversity: the synced value on the client, live on the server. */
    public int displayDiversityPermille() {
        return clientDiversity >= 0 ? clientDiversity : diversityPermille();
    }

    /** Network energy fill per-mille; 1000 (healthy) while no buffers exist. */
    private int networkEnergyPermille() {
        Network network = NetworkManager.networkOf(this);
        if (network == null) {
            return 1000;
        }
        int capacity = EnergyReport.capacity(network);
        return capacity == 0 ? 1000 : (int) ((long) EnergyReport.stored(network) * 1000 / capacity);
    }

    /** Machines are frozen for lack of energy right now (the KOIOS alert). */
    private boolean networkEnergyDeficit() {
        Network network = NetworkManager.networkOf(this);
        return network != null && EnergyReport.starvedCount(network) > 0;
    }

    private boolean lastSyncedDeficit;

    /**
     * Called by machines on starved-state transitions (event-driven, the
     * core never ticks — ADR-012): resync the in-world screen only when the
     * network-wide verdict actually changed.
     */
    public void energyStateChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean deficit = networkEnergyDeficit();
        if (deficit != lastSyncedDeficit) {
            lastSyncedDeficit = deficit;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Renderer-side energy health (synced on the client, live on the server). */
    public int displayEnergyPermille() {
        return level != null && level.isClientSide ? clientEnergyPermille : networkEnergyPermille();
    }

    public boolean displayEnergyDeficit() {
        return level != null && level.isClientSide ? clientEnergyDeficit : networkEnergyDeficit();
    }

    private void pushWindow(String key) {
        String evicted = window[windowHead];
        if (evicted == null) {
            windowFill++;
        } else {
            windowCounts.merge(evicted, -1, (a, b) -> a + b <= 0 ? null : a + b);
        }
        window[windowHead] = key;
        windowCounts.merge(key, 1, Integer::sum);
        windowHead = (windowHead + 1) % KoiosBalance.WINDOW_SIZE;
    }

    private void resetWindow() {
        java.util.Arrays.fill(window, null);
        windowCounts.clear();
        windowHead = 0;
        windowFill = 0;
    }

    private List<String> windowToList() {
        List<String> list = new ArrayList<>(windowFill);
        int start = (windowHead - windowFill + KoiosBalance.WINDOW_SIZE) % KoiosBalance.WINDOW_SIZE;
        for (int i = 0; i < windowFill; i++) {
            list.add(window[(start + i) % KoiosBalance.WINDOW_SIZE]);
        }
        return list;
    }

    // --- Multiblock ---

    /** Event-driven: called on placement, shell changes and GUI open. */
    public void revalidateStructure() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean formed = checkShell();
        // Live state, same reason as isFormed(): comparing against a stale
        // cache both skips needed flips and makes the no-op setBlock below
        // unable to ever refresh that cache.
        BlockState state = level.getBlockState(worldPosition);
        if (!state.hasProperty(KoiosCoreBlock.FORMED)
                || state.getValue(KoiosCoreBlock.FORMED) == formed) {
            return;
        }
        level.setBlock(worldPosition, state.setValue(KoiosCoreBlock.FORMED, formed), 3);
        // The structure becomes one entity: flip all casings with it (their
        // seamless "formed" texture makes the 3x3x3 read as one big block).
        updateShell(formed);
        // Controller activity follows FORMED — recompute the component.
        NetworkManager.nodeChanged(this);
        if (formed) {
            if (screenSide == null) {
                screenSide = pickScreenSideTowardBuilder();
            }
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.2F);
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_BOOT, 0);
        } else {
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.0F);
        }
        syncToClients();
    }

    /** Called by the core block when it is broken while formed. */
    public void releaseShell() {
        if (level != null && !level.isClientSide) {
            updateShell(false);
        }
    }

    private void updateShell(boolean formed) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Vec3i offset : SHELL_OFFSETS) {
            cursor.setWithOffset(worldPosition, offset.getX(), offset.getY(), offset.getZ());
            BlockState casing = level.getBlockState(cursor);
            if (casing.is(ModBlocks.NEURAL_CASING.get())
                    && casing.getValue(NeuralCasingBlock.FORMED) != formed) {
                level.setBlock(cursor, casing.setValue(NeuralCasingBlock.FORMED, formed), 3);
            }
        }
    }

    /** Default screen: the horizontal face toward whoever just built it. */
    private Direction pickScreenSideTowardBuilder() {
        Player nearest = level.getNearestPlayer(
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 16, false);
        if (nearest == null) {
            return Direction.NORTH;
        }
        double dx = nearest.getX() - (worldPosition.getX() + 0.5);
        double dz = nearest.getZ() - (worldPosition.getZ() + 0.5);
        if (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) {
            return Direction.NORTH;
        }
        return Direction.getNearest((float) dx, 0, (float) dz);
    }

    @Nullable
    public Direction getScreenSide() {
        return screenSide;
    }

    public void setScreenSide(Direction side) {
        if (screenSide != side) {
            screenSide = side;
            setChanged();
            syncToClients();
        }
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private boolean checkShell() {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Vec3i offset : SHELL_OFFSETS) {
            cursor.setWithOffset(worldPosition, offset.getX(), offset.getY(), offset.getZ());
            if (!level.getBlockState(cursor).is(ModBlocks.NEURAL_CASING.get())) {
                return false;
            }
        }
        return true;
    }

    private static Vec3i[] buildShellOffsets() {
        List<Vec3i> offsets = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        offsets.add(new Vec3i(x, y, z));
                    }
                }
            }
        }
        return offsets.toArray(new Vec3i[0]);
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.koios_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new KoiosCoreMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- Client head animations (see KoiosCoreRenderer) ---

    @Override
    public boolean triggerEvent(int id, int param) {
        long now = level == null ? 0 : level.getGameTime();
        switch (id) {
            case EVENT_PULSE -> {
                clientPulseStart = now;
                clientPulseOps = Math.max(1, param);
                return true;
            }
            case EVENT_LEVEL_UP -> {
                clientLevelUpStart = now;
                return true;
            }
            case EVENT_BOOT -> {
                clientBootStart = now;
                return true;
            }
            case EVENT_TIER_UP -> {
                clientTierUpStart = now;
                return true;
            }
            default -> {
                return super.triggerEvent(id, param);
            }
        }
    }

    public long getClientBootStart() {
        return clientBootStart;
    }

    public int getClientPulseOps() {
        return clientPulseOps;
    }

    public long getClientPulseStart() {
        return clientPulseStart;
    }

    public long getClientLevelUpStart() {
        return clientLevelUpStart;
    }

    public long getClientTierUpStart() {
        return clientTierUpStart;
    }

    // --- Persistence and client sync ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("DataXp", xp);
        tag.putInt("Tier", tier);
        tag.putInt("CodexSeen", codexSeen);
        if (screenSide != null) {
            tag.putInt("ScreenSide", screenSide.get3DDataValue());
        }
        ListTag list = new ListTag();
        for (String key : windowToList()) {
            list.add(StringTag.valueOf(key));
        }
        tag.put("Window", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Pre-tier saves carry no Tier key: they are tier 1 cores.
        tier = Math.max(1, Math.min(KoiosBalance.MAX_TIER,
                tag.contains("Tier") ? tag.getInt("Tier") : 1));
        xp = Math.min(KoiosBalance.xpCap(tier), tag.getInt("DataXp"));
        codexSeen = tag.getInt("CodexSeen");
        screenSide = tag.contains("ScreenSide") ? Direction.from3DDataValue(tag.getInt("ScreenSide")) : null;
        if (tag.contains("Div")) {
            clientDiversity = tag.getInt("Div");
        }
        if (tag.contains("EnergyMille")) {
            clientEnergyPermille = tag.getInt("EnergyMille");
            clientEnergyDeficit = tag.getBoolean("Deficit");
        }
        // Client-only in practice: the update tag carries the fault flag; on
        // the server the graph rederives it after load (never persisted).
        networkFault = tag.getBoolean("Fault");
        resetWindow();
        ListTag list = tag.getList("Window", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            pushWindow(list.getString(i));
        }
        koiosLevel = KoiosBalance.levelForXp(xp, tier);
    }

    /** Chunk-load / sendBlockUpdated payload: what the renderer needs. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataXp", xp);
        tag.putInt("Tier", tier);
        tag.putInt("Div", diversityPermille());
        tag.putInt("EnergyMille", networkEnergyPermille());
        tag.putBoolean("Deficit", networkEnergyDeficit());
        if (screenSide != null) {
            tag.putInt("ScreenSide", screenSide.get3DDataValue());
        }
        if (networkFault) {
            tag.putBoolean("Fault", true);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Item round-trip: a broken core keeps its learning on the drop ---

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (xp > 0 || tier > 1) {
            builder.set(ModDataComponents.KOIOS_DATA.get(), new KoiosData(xp, windowToList(), tier));
        }
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        KoiosData data = input.get(ModDataComponents.KOIOS_DATA.get());
        // Replacement keeps max(existing, item) — tier first, then XP; the
        // progression is never lost (design).
        if (data != null && data.outranks(tier, xp)) {
            tier = Math.max(1, Math.min(KoiosBalance.MAX_TIER, data.tier()));
            xp = Math.min(KoiosBalance.xpCap(tier), data.xp());
            resetWindow();
            for (String key : data.window()) {
                pushWindow(key);
            }
            koiosLevel = KoiosBalance.levelForXp(xp, tier);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("DataXp");
        tag.remove("Window");
        tag.remove("Tier");
    }
}
