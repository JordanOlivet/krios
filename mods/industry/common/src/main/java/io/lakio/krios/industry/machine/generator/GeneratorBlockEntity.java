package io.lakio.krios.industry.machine.generator;

import io.lakio.krios.industry.energy.EnergyBuffer;
import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.koios.KoiosCoreBlockEntity;
import io.lakio.krios.industry.network.EnergyPort;
import io.lakio.krios.industry.network.FaceConfig;
import io.lakio.krios.industry.network.Flow;
import io.lakio.krios.industry.network.FlowPorts;
import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkManager;
import io.lakio.krios.industry.network.NetworkNode;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.core.Direction;
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

public class GeneratorBlockEntity extends BlockEntity
        implements Container, MenuProvider, NetworkNode, EnergyPort, FlowPorts {

    public static final int CAPACITY = 100_000;
    public static final int GENERATION_PER_TICK = 40;
    public static final int MAX_EXTRACT_PER_TICK = 400;

    // Primitive runtime state (ADR-012). No object allocation on the tick path.
    private final EnergyBuffer energy = new EnergyBuffer(CAPACITY, MAX_EXTRACT_PER_TICK);
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final FaceConfig faceConfig = new FaceConfig();
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
                case 10 -> networkKoiosLevel();
                // 11..15: machine pos for the hologram view (x/z as low/high
                // short pairs, y fits a short on its own).
                case 11 -> worldPosition.getX() & 0xFFFF;
                case 12 -> (worldPosition.getX() >> 16) & 0xFFFF;
                case 13 -> worldPosition.getZ() & 0xFFFF;
                case 14 -> (worldPosition.getZ() >> 16) & 0xFFFF;
                case 15 -> worldPosition.getY();
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
            return GeneratorMenu.DATA_COUNT;
        }
    };

    // Directions this machine supports, for the one-click presets.
    private static final short PRESET_IN_BITS = (short) FaceConfig.flowBits(Flow.ITEM, false);
    private static final short PRESET_OUT_BITS = (short) FaceConfig.flowBits(Flow.ENERGY, true);

    /** Same button channel as the crusher (face*16 + sub, 9..12 = presets). */
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
        return true;
    }

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR.get(), pos, state);
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

    /** Pool contribution: rate-capped by the buffer, wakes the burner. */
    @Override
    public int extractNetworkEnergy(int max) {
        int pulled = energy.extract(max);
        if (pulled > 0) {
            setChanged();
            tryResume();
        }
        return pulled;
    }

    @Override
    public int storedNetworkEnergy() {
        return energy.getStored();
    }

    @Override
    public int networkEnergyCapacity() {
        return energy.getCapacity();
    }

    // --- Flow ports: energy source on every face ---

    @Override
    public boolean canOffer(Direction face, Flow flow) {
        return flow == Flow.ENERGY;
    }

    @Override
    public boolean canAccept(Direction face, Flow flow) {
        return false;
    }

    @Override
    public FaceConfig faceConfig() {
        return faceConfig;
    }

    @Override
    public void faceConfigDirty() {
        // Never setChanged() here: auto-config can run during chunk load
        // and the comparator scan would sync-load chunks (deadlock).
        if (level != null) {
            NetworkManager.markUnsaved(level, worldPosition);
        }
    }

    /**
     * Face contract for external energy extraction (FE/TRE adapters and the
     * disconnected contact transfer). Only a MANUAL config restricts —
     * virgin and auto-configured faces keep vanilla behavior (exposed).
     */
    public boolean allowsEnergyExtract(Direction side) {
        return !faceConfig.isManual(side) || faceConfig.allows(side, Flow.ENERGY, true);
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
        // varied fuels teach more than a coal monoculture. Reported to this
        // machine's network controller — no controller, no learning.
        Network network = NetworkManager.networkOf(this);
        if (network != null && network.controller() instanceof KoiosCoreBlockEntity core) {
            core.reportOperations(
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

    /**
     * A full generator idles unlit and resumes when a drain frees room —
     * consumers poke it after pulling (v0 adjacency; the network's scheduled
     * wake-ups take over later).
     */
    public void tryResume() {
        tryIgnite();
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
        faceConfig.save(tag);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnDuration", burnDuration);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy.load(tag);
        faceConfig.load(tag);
        burnTime = tag.getInt("BurnTime");
        burnDuration = tag.getInt("BurnDuration");
    }
}
