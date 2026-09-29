package io.lakio.krios.industry.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Per-world server balance config (ADR-015): the ADR-007 difficulty
 * multipliers, stored in {@code <world>/serverconfig/krios_industry-server.properties}
 * and editable on disk or live through {@code /krios_industry config}. The file speaks
 * human decimals ("1.5"); game logic reads permille ints and stays
 * allocation-free (ADR-012 discipline).
 *
 * Server-thread only. The cache follows the current server instance, so
 * switching singleplayer worlds reloads the right file; without a server
 * (client-side call, unreadable file) every key answers its default. The file
 * is written on first load, so it materializes as soon as a world uses any
 * configurable value.
 */
public final class IndustryConfig {

    public enum Key {
        /** Multiplies every machine's per-operation energy cost. */
        ENERGY_COST("energy_cost"),
        /** Multiplies the base data XP of operations reported to KOIOS. */
        XP_RATE("xp_rate");

        private final String id;

        Key(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public static final double MIN_VALUE = 0.0;
    public static final double MAX_VALUE = 10.0;
    public static final int DEFAULT_PERMILLE = 1000;

    private static final String FILE_NAME = "krios_industry-server.properties";
    private static final Key[] KEYS = Key.values();

    // Cache keyed on server identity: a new singleplayer world means a new
    // MinecraftServer, so a mismatch (or a collected reference) reloads.
    private static WeakReference<MinecraftServer> owner = new WeakReference<>(null);
    private static final int[] values = defaults();

    private IndustryConfig() {
    }

    /** Multiplier for one key, permille. Defaults without a server. */
    public static int permille(@Nullable MinecraftServer server, Key key) {
        if (server == null) {
            return DEFAULT_PERMILLE;
        }
        if (owner.get() != server) {
            load(server);
        }
        return values[key.ordinal()];
    }

    /** Human-readable multiplier for command feedback. */
    public static double value(@Nullable MinecraftServer server, Key key) {
        return permille(server, key) / 1000.0;
    }

    /** Sets, clamps, applies live and persists one key. */
    public static void set(MinecraftServer server, Key key, double value) {
        permille(server, key);
        values[key.ordinal()] = clamp((int) Math.round(value * 1000.0));
        save(server);
    }

    /** Integer difficulty scaling: {@code base * permille / 1000}. */
    public static int scale(int base, int permille) {
        return (int) ((long) base * permille / 1000L);
    }

    private static void load(MinecraftServer server) {
        System.arraycopy(defaults(), 0, values, 0, values.length);
        Path file = file(server);
        boolean present = Files.exists(file);
        if (present) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException ignored) {
            }
            for (Key key : KEYS) {
                String raw = props.getProperty(key.id());
                if (raw != null) {
                    try {
                        values[key.ordinal()] = clamp(
                                (int) Math.round(Double.parseDouble(raw.trim()) * 1000.0));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        owner = new WeakReference<>(server);
        if (!present) {
            save(server);
        }
    }

    private static void save(MinecraftServer server) {
        Properties props = new Properties();
        for (Key key : KEYS) {
            props.setProperty(key.id(), Double.toString(values[key.ordinal()] / 1000.0));
        }
        Path file = file(server);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Krios Industry difficulty multipliers ("
                        + MIN_VALUE + "-" + MAX_VALUE + ", 1.0 = default); "
                        + "editable in game with /krios_industry config");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(FILE_NAME);
    }

    private static int clamp(int permille) {
        return Math.max((int) (MIN_VALUE * 1000), Math.min((int) (MAX_VALUE * 1000), permille));
    }

    private static int[] defaults() {
        int[] defaults = new int[KEYS.length];
        Arrays.fill(defaults, DEFAULT_PERMILLE);
        return defaults;
    }
}
