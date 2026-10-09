package io.lakio.krios.industry.machine.farmer;

import java.util.ArrayList;
import java.util.List;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.HydrationZones;
import io.lakio.krios.industry.machine.ZoneGeometry;
import io.lakio.krios.industry.machine.zone.PlantingZoneBlockEntity;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Chrono Farmer (design 2026-10-08): harvests mature crops in the zone
 * behind its screen, keeps the farmland moist, replants (seeds from the
 * harvest first, the seed slots back it up), tills dirt it can plant right
 * away. Everything else — pacing, routing, energy, zone — is the shared
 * {@link PlantingZoneBlockEntity}.
 */
public class ChronoFarmerBlockEntity extends PlantingZoneBlockEntity {

    public ChronoFarmerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHRONO_FARMER.get(), pos, state);
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

    public static boolean isPlantable(ItemStack stack) {
        return plantFor(stack) != null;
    }

    @Override
    @Nullable
    protected BlockState plantableState(ItemStack stack) {
        return plantFor(stack);
    }

    @Override
    protected boolean isGrowing(BlockState state) {
        return isCrop(state) && !isMature(state);
    }

    @Override
    protected int operationXp() {
        return KoiosBalance.HARVEST_BASE_XP;
    }

    // --- Hydration (ADR-023) ---

    /** The zone's soil layer counts as near water (HydrationZones). */
    @Override
    protected void onZoneChanged() {
        int edge = zoneEdgeForDisplay();
        if (level == null || level.isClientSide || edge <= 0) {
            return;
        }
        BlockPos min = ZoneGeometry.min(worldPosition, zoneDirection(), edge).below();
        BlockPos max = ZoneGeometry.max(worldPosition, zoneDirection(), edge).below();
        HydrationZones.put(level, worldPosition, BoundingBox.fromCorners(min, max));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            HydrationZones.remove(level, worldPosition);
        }
    }

    // --- Cells ---

    @Override
    protected CellResult workCell(ServerLevel level, BlockPos cropPos, int row, int col, Work work) {
        BlockState crop = level.getBlockState(cropPos);
        BlockPos soilPos = cropPos.below();
        BlockState soil = level.getBlockState(soilPos);
        hydrate(level, soilPos, soil, work.efficiencyPermille);
        if (isCrop(crop)) {
            markWorkable();
            return isMature(crop) ? harvest(level, cropPos, crop, work) : CellResult.SKIP;
        }
        if (!crop.isAir()) {
            return CellResult.SKIP;
        }
        if (soil.is(Blocks.FARMLAND) || soil.is(Blocks.SOUL_SAND)) {
            markWorkable();
            int slot = plantSlotFor(level, cropPos);
            if (slot < 0) {
                markNoPlants();
                return CellResult.SKIP;
            }
            if (!energy.consume(cost(level, KoiosBalance.PLANT_ENERGY, work.efficiencyPermille))) {
                return CellResult.STARVED;
            }
            plant(level, cropPos, slot, SoundEvents.CROP_PLANTED);
            return CellResult.ACTED;
        }
        if (soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.DIRT_PATH)) {
            // Till only what we can plant right now: no point turning a
            // lawn into bare farmland the machine has nothing to sow on.
            int slot = farmlandSeedSlot(level, cropPos);
            if (slot < 0) {
                return CellResult.SKIP;
            }
            int tillCost = cost(level, KoiosBalance.TILL_ENERGY, work.efficiencyPermille);
            int plantCost = cost(level, KoiosBalance.PLANT_ENERGY, work.efficiencyPermille);
            if (energy.getStored() < tillCost + plantCost) {
                return CellResult.STARVED;
            }
            energy.consume(tillCost + plantCost);
            level.setBlock(soilPos, Blocks.FARMLAND.defaultBlockState()
                    .setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 3);
            level.playSound(null, soilPos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 0.5F, 1.0F);
            plant(level, cropPos, slot, SoundEvents.CROP_PLANTED);
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

    private CellResult harvest(ServerLevel level, BlockPos cropPos, BlockState crop, Work work) {
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(crop, level, cropPos, null));
        // Yield modules: +25% per point as whole extra harvests, carried in
        // quarters (ADR-006, deterministic) — committed only on success.
        int quarters = yieldQuarters + work.yieldPoints;
        for (int bonus = quarters / 4; bonus > 0; bonus--) {
            drops.addAll(Block.getDrops(crop, level, cropPos, null));
        }
        // Replant from the harvest first; the seed slots back it up.
        ItemStack seed = crop.getBlock().getCloneItemStack(level, cropPos, crop);
        boolean replant = takeOne(drops, seed);
        int seedSlot = -1;
        if (!replant) {
            seedSlot = plantSlotMatching(seed);
            replant = seedSlot >= 0;
        }
        if (!stow(drops, seedSlot, true)) {
            return CellResult.BLOCKED;
        }
        if (!energy.consume(cost(level, KoiosBalance.HARVEST_ENERGY, work.efficiencyPermille))) {
            return CellResult.STARVED;
        }
        yieldQuarters = quarters % 4;
        stow(drops, seedSlot, false);
        level.levelEvent(2001, cropPos, Block.getId(crop));
        level.setBlock(cropPos, replant ? crop.getBlock().defaultBlockState()
                : Blocks.AIR.defaultBlockState(), 3);
        work.report("harvest:" + BuiltInRegistries.BLOCK.getKey(crop.getBlock()));
        return CellResult.ACTED;
    }

    /** First seed slot holding a farmland crop that would get enough light. */
    private int farmlandSeedSlot(Level level, BlockPos cropPos) {
        if (level.getRawBrightness(cropPos, 0) < 8 && !level.canSeeSky(cropPos)) {
            return -1;
        }
        for (int slot = 0; slot < PLANT_SLOTS; slot++) {
            BlockState plant = plantFor(items.get(slot));
            if (plant != null && plant.getBlock() instanceof CropBlock) {
                return slot;
            }
        }
        return -1;
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
}
