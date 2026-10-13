package io.lakio.krios.industry.machine.field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.lakio.krios.industry.koios.KoiosBalance;
import io.lakio.krios.industry.machine.zone.CreatureZoneBlockEntity;
import io.lakio.krios.industry.platform.Services;
import io.lakio.krios.industry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Phase Death Field (design 2026-10-13, ADR-026 — the Reaper Array merged
 * in): hits every targeted mob in the zone behind its screen, through the
 * machines' fake player so kills count as a player's (player-only loot,
 * vanilla XP orbs left where the mob dies until krios_storage). Drops are
 * caught into the buffer at death; anything that does not fit stays on the
 * ground.
 *
 * <ul>
 * <li>Target filter: passive (everything not {@link Enemy}), hostile, or
 * all. Nothing is protected beyond it (user): the player answers for where
 * the field stands. Players are never scanned (mobs only).</li>
 * <li>Spare breeders (optional): N adults per species are kept among
 * {@link Animal}s, and animal babies are spared.</li>
 * <li>Damage doubles with the effective tier (4/8/16/32); invulnerability
 * frames are reset so the machine sets the pace. Speed shortens the
 * cadence, Yield is Looting on the fake player's weapon, Efficiency the
 * cost.</li>
 * </ul>
 */
public class PhaseDeathFieldBlockEntity extends CreatureZoneBlockEntity<Mob> {

    public static final int FILTER_PASSIVE = 0;
    public static final int FILTER_HOSTILE = 1;
    public static final int FILTER_ALL = 2;

    public static final int STATE_NO_TARGETS = 3;

    public static final int BUTTON_FILTER = 110;
    public static final int BUTTON_SPARE_TOGGLE = 111;
    public static final int BUTTON_SPARE_DOWN = 112;
    public static final int BUTTON_SPARE_UP = 113;

    public static final int SPARE_DEFAULT = 2;
    public static final int SPARE_MAX = 64;
    /** Lag guard: hits per wake, whatever the crowd. */
    private static final int MAX_HITS_PER_WAKE = 64;

    // Machine data after the creature block.
    public static final int DATA_FILTER = CREATURE_DATA_COUNT;
    public static final int DATA_SPARE_ON = CREATURE_DATA_COUNT + 1;
    public static final int DATA_SPARE_N = CREATURE_DATA_COUNT + 2;
    public static final int DATA_COUNT = CREATURE_DATA_COUNT + 3;

    private int filter = FILTER_HOSTILE;
    private boolean spareOn;
    private int spareCount = SPARE_DEFAULT;

    private int idle = STATE_NO_TARGETS;
    /** Last wake's targets per species (census detail). */
    private Map<EntityType<?>, Integer> targeted = Map.of();

    public PhaseDeathFieldBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHASE_DEATH_FIELD.get(), pos, state, 0, BUFFER_SLOTS, Mob.class);
    }

    @Override
    protected int operationXp() {
        return KoiosBalance.KILL_BASE_XP;
    }

    /** Damage per hit: 4, doubled per effective tier. */
    public static int damageForTier(int tier) {
        return KoiosBalance.FIELD_BASE_DAMAGE << (Math.max(1, tier) - 1);
    }

    // --- Buttons ---

    @Override
    protected boolean handleMachineButton(int id) {
        switch (id) {
            case BUTTON_FILTER -> filter = (filter + 1) % 3;
            case BUTTON_SPARE_TOGGLE -> spareOn = !spareOn;
            case BUTTON_SPARE_DOWN -> spareCount = Math.max(1, spareCount - 1);
            case BUTTON_SPARE_UP -> spareCount = Math.min(SPARE_MAX, spareCount + 1);
            default -> {
                return false;
            }
        }
        setChanged();
        kick();
        return true;
    }

    // --- Targets ---

    private boolean matchesFilter(Mob mob) {
        boolean hostile = mob instanceof Enemy;
        return switch (filter) {
            case FILTER_PASSIVE -> !hostile;
            case FILTER_HOSTILE -> hostile;
            default -> true;
        };
    }

    /** One species' targets: filter first, then the spare rule on animals. */
    private List<Mob> targets(List<Mob> creatures) {
        List<Mob> targets = new ArrayList<>();
        int adultsKept = 0;
        for (Mob mob : creatures) {
            if (!matchesFilter(mob)) {
                continue;
            }
            if (spareOn && mob instanceof Animal animal) {
                if (animal.isBaby()) {
                    continue;
                }
                if (adultsKept < spareCount) {
                    adultsKept++;
                    continue;
                }
            }
            targets.add(mob);
        }
        return targets;
    }

    // --- Work ---

    @Override
    protected void work(ServerLevel level, Work work) {
        Map<EntityType<?>, List<Mob>> species = scan(level);
        Map<EntityType<?>, Integer> counts = new HashMap<>();
        List<Mob> all = new ArrayList<>();
        for (Map.Entry<EntityType<?>, List<Mob>> entry : species.entrySet()) {
            List<Mob> targets = targets(entry.getValue());
            counts.put(entry.getKey(), targets.size());
            all.addAll(targets);
        }
        targeted = counts;
        if (!all.isEmpty()) {
            reap(level, work, all);
        }
        idle = STATE_NO_TARGETS;
        census(species);
    }

    private void reap(ServerLevel level, Work work, List<Mob> targets) {
        ServerPlayer reaper = Services.FAKE_PLAYERS.get(level);
        reaper.moveTo(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5, 0.0F, 0.0F);
        reaper.setItemInHand(InteractionHand.MAIN_HAND, weapon(level, work.yieldPoints));
        DamageSource source = level.damageSources().playerAttack(reaper);
        int damage = damageForTier(work.effectiveTier);
        int hitCost = cost(level, KoiosBalance.FIELD_ENERGY_PER_DAMAGE * damage, work.efficiencyPermille);
        int hits = 0;
        for (Mob mob : targets) {
            if (hits >= MAX_HITS_PER_WAKE) {
                break;
            }
            if (!mob.isAlive()) {
                continue;
            }
            if (!hasFreeBufferSlot()) {
                work.blocked = true;
                break;
            }
            if (!energy.consume(hitCost)) {
                work.starved = true;
                break;
            }
            // The field sets the pace (not the i-frames) and never shoves:
            // knockback would push the herd out of the zone.
            mob.invulnerableTime = 0;
            Vec3 motion = mob.getDeltaMovement();
            mob.hurt(source, damage);
            mob.setDeltaMovement(motion);
            forgetReaper(mob, reaper);
            hits++;
            work.actions++;
            if (!mob.isAlive()) {
                catchDrops(level, mob);
                work.report("kill:" + BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
            }
        }
        reaper.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (hits > 0) {
            setChanged();
        }
    }

    /**
     * The hit made the mob target and resent the fake player, which stands
     * at the machine: mobs would crowd the block (seen in play). Undo only
     * what points at the reaper — a real player's aggro stays — and keep
     * lastHurtByPlayer, the kill credit for loot and XP.
     */
    private static void forgetReaper(Mob mob, ServerPlayer reaper) {
        if (!mob.isAlive()) {
            return;
        }
        if (mob.getTarget() == reaper) {
            mob.setTarget(null);
        }
        if (mob.getLastHurtByMob() == reaper) {
            mob.setLastHurtByMob(null);
        }
        if (mob instanceof NeutralMob neutral && reaper.getUUID().equals(neutral.getPersistentAngerTarget())) {
            neutral.stopBeingAngry();
        }
    }

    /** The reaper's weapon: carries Looting at the Yield point total. */
    private static ItemStack weapon(ServerLevel level, int yieldPoints) {
        ItemStack weapon = new ItemStack(Items.IRON_SWORD);
        if (yieldPoints > 0) {
            weapon.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.LOOTING), yieldPoints);
        }
        return weapon;
    }

    /** Death drops spawned this very tick around the corpse, into the buffer. */
    private void catchDrops(ServerLevel level, Mob mob) {
        ItemStack[] buffer = bufferView(false);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class,
                mob.getBoundingBox().inflate(1.0),
                entity -> entity.isAlive() && entity.tickCount == 0)) {
            ItemStack rest = merge(buffer, drop.getItem().copy());
            if (rest.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(rest);
            }
        }
        commitBuffer(buffer);
    }

    /** Hit cadence: 20 ticks, shortened by Speed; the idle backoff otherwise. */
    @Override
    protected int nextDelay(Work work) {
        if (work.actions <= 0) {
            return KoiosBalance.ZONE_IDLE_DELAY_TICKS;
        }
        return Math.max(1, KoiosBalance.FIELD_HIT_INTERVAL_TICKS * 2 / (2 + work.speedPoints));
    }

    @Override
    protected int censusDetail(EntityType<?> type, List<Mob> creatures) {
        return targeted.getOrDefault(type, 0);
    }

    @Override
    protected int idleState() {
        return idle;
    }

    // --- Synced data ---

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int machineData(int index) {
        if (index == DATA_FILTER) {
            return filter;
        }
        if (index == DATA_SPARE_ON) {
            return spareOn ? 1 : 0;
        }
        if (index == DATA_SPARE_N) {
            return spareCount;
        }
        return super.machineData(index);
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Filter", filter);
        tag.putBoolean("SpareOn", spareOn);
        tag.putInt("SpareCount", spareCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filter = tag.contains("Filter") ? tag.getInt("Filter") : FILTER_HOSTILE;
        spareOn = tag.getBoolean("SpareOn");
        spareCount = tag.contains("SpareCount") ? Math.max(1, tag.getInt("SpareCount")) : SPARE_DEFAULT;
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.krios_industry.phase_death_field");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PhaseDeathFieldMenu(containerId, playerInventory, this, dataAccess);
    }
}
