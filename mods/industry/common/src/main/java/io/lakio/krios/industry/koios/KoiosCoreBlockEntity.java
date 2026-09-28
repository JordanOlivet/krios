package io.lakio.krios.industry.koios;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final int PULSE_MIN_INTERVAL_TICKS = 8;

    private int xp;
    private int koiosLevel = 1;
    /** Which face of the shell displays the AI's head; null = none yet. */
    @Nullable
    private Direction screenSide;
    private long lastPulseGameTime;
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
    /** Diversity received via update tag (client); -1 = compute locally. */
    private int clientDiversity = -1;

    // Sliding diversity window: ring buffer of the last N operation keys plus
    // per-key counts for O(1) share lookups (design: diminishing returns on
    // repetition, recovers as operations vary).
    private final String[] window = new String[KoiosBalance.WINDOW_SIZE];
    private int windowHead;
    private int windowFill;
    private final Map<String, Integer> windowCounts = new HashMap<>();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> koiosLevel;
                case 1 -> isFormed() ? 1 : 0;
                case 2 -> diversityPermille();
                case 3 -> xp & 0xFFFF;
                case 4 -> (xp >> 16) & 0xFFFF;
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
        int cap = KoiosBalance.xpCap();
        for (int i = 0; i < count && xp < cap; i++) {
            xp = Math.min(cap, xp + baseXp * diversityFactorPermille(key) / 1000);
            pushWindow(key);
        }
        int previousLevel = koiosLevel;
        koiosLevel = KoiosBalance.levelForXp(xp);
        setChanged();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - lastPulseGameTime >= PULSE_MIN_INTERVAL_TICKS) {
            lastPulseGameTime = now;
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_PULSE, 0);
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
        int clamped = Math.max(0, Math.min(KoiosBalance.xpCap(), newXp));
        if (clamped == xp) {
            return;
        }
        int previousLevel = koiosLevel;
        xp = clamped;
        koiosLevel = KoiosBalance.levelForXp(xp);
        setChanged();
        if (level != null && koiosLevel > previousLevel) {
            level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.3F);
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_LEVEL_UP, koiosLevel);
        }
        syncToClients();
    }

    public boolean isFormed() {
        return getBlockState().getValue(KoiosCoreBlock.FORMED);
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
        BlockState state = getBlockState();
        if (state.getValue(KoiosCoreBlock.FORMED) == formed) {
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
            default -> {
                return super.triggerEvent(id, param);
            }
        }
    }

    public long getClientBootStart() {
        return clientBootStart;
    }

    public long getClientPulseStart() {
        return clientPulseStart;
    }

    public long getClientLevelUpStart() {
        return clientLevelUpStart;
    }

    // --- Persistence and client sync ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("DataXp", xp);
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
        xp = Math.min(KoiosBalance.xpCap(), tag.getInt("DataXp"));
        screenSide = tag.contains("ScreenSide") ? Direction.from3DDataValue(tag.getInt("ScreenSide")) : null;
        if (tag.contains("Div")) {
            clientDiversity = tag.getInt("Div");
        }
        // Client-only in practice: the update tag carries the fault flag; on
        // the server the graph rederives it after load (never persisted).
        networkFault = tag.getBoolean("Fault");
        resetWindow();
        ListTag list = tag.getList("Window", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            pushWindow(list.getString(i));
        }
        koiosLevel = KoiosBalance.levelForXp(xp);
    }

    /** Chunk-load / sendBlockUpdated payload: what the renderer needs. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataXp", xp);
        tag.putInt("Div", diversityPermille());
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
        if (xp > 0) {
            builder.set(ModDataComponents.KOIOS_DATA.get(), new KoiosData(xp, windowToList()));
        }
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        KoiosData data = input.get(ModDataComponents.KOIOS_DATA.get());
        // Replacement keeps max(existing, item) — XP is never lost (design).
        if (data != null && data.xp() > xp) {
            xp = Math.min(KoiosBalance.xpCap(), data.xp());
            resetWindow();
            for (String key : data.window()) {
                pushWindow(key);
            }
            koiosLevel = KoiosBalance.levelForXp(xp);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("DataXp");
        tag.remove("Window");
    }
}
