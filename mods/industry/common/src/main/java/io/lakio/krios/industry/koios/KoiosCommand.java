package io.lakio.krios.industry.koios;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

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
 * /koios — op-level (permission 2) inspection and progression tooling for the
 * closest formed core in range, using the same lookup machines use. The
 * builder is pure Brigadier/vanilla so it lives in common; each loader only
 * hooks it into its command-registration event.
 */
public final class KoiosCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("koios")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource())))
                .then(Commands.literal("xp")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(context -> addXp(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "amount")))))
                .then(Commands.literal("level")
                        .then(Commands.argument("level",
                                        IntegerArgumentType.integer(1, KoiosBalance.MAX_CLASSIC_LEVEL))
                                .executes(context -> setLevel(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "level")))))
                .then(Commands.literal("energy")
                        .executes(context -> energy(context.getSource()))));
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
                core.getXp(), KoiosBalance.xpCap(), core.diversityPermille() / 10), false);
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
                core.getXp(), KoiosBalance.xpCap()), true);
        return 1;
    }

    private static int setLevel(CommandSourceStack source, int newLevel) {
        KoiosCoreBlockEntity core = target(source);
        if (core == null) {
            return noCore(source);
        }
        core.setXpAdmin(KoiosBalance.LEVEL_XP[newLevel]);
        source.sendSuccess(() -> Component.translatable("commands.krios_industry.koios.level_set",
                core.getBlockPos().toShortString(), core.getKoiosLevel(), core.getXp()), true);
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
