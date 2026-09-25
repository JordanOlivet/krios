package io.lakio.krios.industry.koios;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.lakio.krios.industry.registry.ModBlockEntities;
import io.lakio.krios.industry.registry.ModBlocks;
import io.lakio.krios.industry.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class KoiosCoreBlockEntity extends BlockEntity implements MenuProvider {

    // The 20 frame positions of the 3x3x3 shell: every offset with at least
    // two non-zero axes (12 edges + 8 corners). Face centers stay free.
    private static final Vec3i[] FRAME_OFFSETS = buildFrameOffsets();

    private int xp;
    private int koiosLevel = 1;

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

    // --- Core index registration (see KoiosNetwork) ---

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide) {
            KoiosNetwork.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            KoiosNetwork.remove(this);
        }
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
        koiosLevel = KoiosBalance.levelForXp(xp);
        setChanged();
    }

    public int getKoiosLevel() {
        return koiosLevel;
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
    private int diversityPermille() {
        return windowFill == 0 ? 1000 : windowCounts.size() * 1000 / windowFill;
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

    /** Event-driven: called on placement, frame changes and GUI open. */
    public void revalidateStructure() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean formed = checkFrame();
        BlockState state = getBlockState();
        if (state.getValue(KoiosCoreBlock.FORMED) != formed) {
            level.setBlock(worldPosition, state.setValue(KoiosCoreBlock.FORMED, formed), 3);
        }
    }

    private boolean checkFrame() {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Vec3i offset : FRAME_OFFSETS) {
            cursor.setWithOffset(worldPosition, offset.getX(), offset.getY(), offset.getZ());
            if (!level.getBlockState(cursor).is(ModBlocks.NEURAL_CASING.get())) {
                return false;
            }
        }
        return true;
    }

    private static Vec3i[] buildFrameOffsets() {
        List<Vec3i> offsets = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) >= 2) {
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

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("DataXp", xp);
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
        resetWindow();
        ListTag list = tag.getList("Window", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            pushWindow(list.getString(i));
        }
        koiosLevel = KoiosBalance.levelForXp(xp);
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
