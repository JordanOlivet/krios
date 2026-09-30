package io.lakio.krios.industry.koios;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.config.IndustryConfig;
import io.lakio.krios.industry.machine.EnergyStats;
import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkManager;
import io.lakio.krios.industry.network.NetworkNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Op-level (permission 2) command tree, pure Brigadier/vanilla so it lives in
 * common; each loader only hooks it into its command-registration event.
 *
 * Canonical root is the modid (ADR-016): /krios_industry config for the
 * mod-level knobs, /krios_industry koios ... for core inspection and
 * progression tooling (closest formed core in range, same lookup machines
 * use). /koios stays as the diegetic alias of the KOIOS subtree only —
 * talking to the AI directly.
 */
public final class KoiosCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Constants.MOD_ID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(configTree())
                .then(koiosTree()));
        dispatcher.register(koiosTree()
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS)));
    }

    /** Server-global difficulty knobs (ADR-015) — no formed core required. */
    private static LiteralArgumentBuilder<CommandSourceStack> configTree() {
        LiteralArgumentBuilder<CommandSourceStack> config = Commands.literal("config")
                .executes(context -> configShow(context.getSource(), null));
        for (IndustryConfig.Key key : IndustryConfig.Key.values()) {
            config.then(Commands.literal(key.id())
                    .executes(context -> configShow(context.getSource(), key))
                    .then(Commands.argument("value", DoubleArgumentType.doubleArg(
                                    IndustryConfig.MIN_VALUE, IndustryConfig.MAX_VALUE))
                            .executes(context -> configSet(context.getSource(), key,
                                    DoubleArgumentType.getDouble(context, "value")))));
        }
        return config;
    }

    /** Fresh builder per call: registered under the root and as the alias. */
    private static LiteralArgumentBuilder<CommandSourceStack> koiosTree() {
        return Commands.literal("koios")
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource())))
                .then(Commands.literal("xp")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(context -> addXp(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "amount")))))
                .then(Commands.literal("level")
                        .then(Commands.argument("level",
                                        IntegerArgumentType.integer(1, KoiosBalance.LEVEL_XP.length - 1))
                                .executes(context -> setLevel(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "level")))))
                .then(Commands.literal("tier")
                        .then(Commands.argument("tier",
                                        IntegerArgumentType.integer(1, KoiosBalance.MAX_TIER))
                                .executes(context -> setTier(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "tier")))))
                .then(Commands.literal("energy")
                        .executes(context -> energy(context.getSource())));
    }

    /** Lists one difficulty multiplier, or all of them with {@code only} null. */
    private static int configShow(CommandSourceStack source, @Nullable IndustryConfig.Key only) {
        for (IndustryConfig.Key key : IndustryConfig.Key.values()) {
            if (only != null && key != only) {
                continue;
            }
            String value = Double.toString(IndustryConfig.value(source.getServer(), key));
            source.sendSuccess(() -> Component.translatable(
                    "commands.krios_industry.koios.config_line", key.id(), value), false);
        }
        return 1;
    }

    private static int configSet(CommandSourceStack source, IndustryConfig.Key key, double value) {
        IndustryConfig.set(source.getServer(), key, value);
        String applied = Double.toString(IndustryConfig.value(source.getServer(), key));
        source.sendSuccess(() -> Component.translatable(
                "commands.krios_industry.koios.config_set", key.id(), applied), true);
        return 1;
    }

    /** Per-member energy breakdown of the controller's network (diagnosis). */
    private static int energy(CommandSourceStack source) {
        KoiosCoreBlockEntity core = target(source);
        if (core == null) {
            return noCore(source);
        }
        Network network = NetworkManager.networkOf(core);
        if (network == null) {
            return noCore(source);
        }
        int supply = 0;
        int draw = 0;
        for (NetworkNode member : network.members()) {
            if (member instanceof EnergyStats stats) {
                supply += stats.energySupplyPerTick();
                draw += stats.energyDrawPerTick();
                String line = String.format("%s %s  stored %d/%d  draw %d  used %d  supply %d%s",
                        member.getClass().getSimpleName(),
                        member.networkPos().toShortString(),
                        stats.energyStored(), stats.energyCapacity(),
                        stats.energyDrawPerTick(), stats.energyConsumedPerTick(),
                        stats.energySupplyPerTick(),
                        stats.energyStarved() ? "  STARVED" : "");
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        int totalSupply = supply;
        int totalDraw = draw;
        source.sendSuccess(() -> Component.literal(
                String.format("TOTAL supply %d E/t, draw %d E/t, members %d",
                        totalSupply, totalDraw, network.members().size())), false);
        return 1;
    }

    private static int info(CommandSourceStack source) {
        KoiosCoreBlockEntity core = target(source);
        if (core == null) {
            return noCore(source);
        }
        source.sendSuccess(() -> Component.translatable("commands.krios_industry.koios.info",
                core.getBlockPos().toShortString(), core.getKoiosLevel(),
                core.getXp(), KoiosBalance.xpCap(core.getTier()), core.diversityPermille() / 10,
                core.getTier()), false);
        return 1;
    }

    private static int addXp(CommandSourceStack source, int amount) {
        KoiosCoreBlockEntity core = target(source);
        if (core == null) {
            return noCore(source);
        }
        int before = core.getXp();
        core.setXpAdmin(before + amount);
        int gained = core.getXp() - before;
        source.sendSuccess(() -> Component.translatable("commands.krios_industry.koios.xp_added",
                gained, core.getBlockPos().toShortString(), core.getKoiosLevel(),
                core.getXp(), KoiosBalance.xpCap(core.getTier())), true);
        return 1;
    }

    private static int setLevel(CommandSourceStack source, int newLevel) {
        KoiosCoreBlockEntity core = target(source);
        if (core == null) {
            return noCore(source);
        }
        // XP is clamped to the core's tier cap: reaching N6+ needs the tier
        // first (/krios_industry koios tier 2).
        core.setXpAdmin(KoiosBalance.LEVEL_XP[newLevel]);
        source.sendSuccess(() -> Component.translatable("commands.krios_industry.koios.level_set",
                core.getBlockPos().toShortString(), core.getKoiosLevel(), core.getXp()), true);
        return 1;
    }

    /** Admin/testing shortcut around the compute-chip ritual. */
    private static int setTier(CommandSourceStack source, int newTier) {
        KoiosCoreBlockEntity core = target(source);
        if (core == null) {
            return noCore(source);
        }
        core.setTierAdmin(newTier);
        source.sendSuccess(() -> Component.translatable("commands.krios_industry.koios.tier_set",
                core.getBlockPos().toShortString(), core.getTier(), core.getKoiosLevel()), true);
        return 1;
    }

    @Nullable
    private static KoiosCoreBlockEntity target(CommandSourceStack source) {
        return NetworkManager.closestActiveController(source.getLevel(),
                BlockPos.containing(source.getPosition()),
                KoiosBalance.CORE_RANGE_SQ) instanceof KoiosCoreBlockEntity core ? core : null;
    }

    private static int noCore(CommandSourceStack source) {
        source.sendFailure(Component.translatable("commands.krios_industry.koios.no_core",
                KoiosBalance.CORE_RANGE));
        return 0;
    }

    private KoiosCommand() {
    }
}
