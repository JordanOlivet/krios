package io.lakio.krios.industry.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Tiny client-side preferences (purely cosmetic state, never gameplay).
 * Stored in the loader-agnostic config/ folder of the game directory; every
 * read and write is failure-tolerant — worst case the preference resets.
 */
public final class ClientPrefs {

    private static final Path FILE = Path.of("config", "krios_industry-client.properties");
    private static final String STATS_EXPANDED = "stats_panel_expanded";

    private static Properties props;

    private ClientPrefs() {
    }

    public static boolean statsExpanded() {
        return Boolean.parseBoolean(load().getProperty(STATS_EXPANDED, "true"));
    }

    public static void setStatsExpanded(boolean expanded) {
        load().setProperty(STATS_EXPANDED, Boolean.toString(expanded));
        save();
    }

    private static Properties load() {
        if (props == null) {
            props = new Properties();
            if (Files.exists(FILE)) {
                try (InputStream in = Files.newInputStream(FILE)) {
                    props.load(in);
                } catch (IOException ignored) {
                }
            }
        }
        return props;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "Krios Industry client preferences");
            }
        } catch (IOException ignored) {
        }
    }
}
