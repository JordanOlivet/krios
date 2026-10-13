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
    private static final String FACES_OPEN = "faces_panel_open";
    private static final String MODULE_BAY_OPEN = "module_bay_open";
    private static final String CENSUS_OPEN = "census_panel_open";
    private static final String BOOT_ANIMATION = "boot_animation";

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

    public static boolean facesOpen() {
        return Boolean.parseBoolean(load().getProperty(FACES_OPEN, "false"));
    }

    public static void setFacesOpen(boolean open) {
        load().setProperty(FACES_OPEN, Boolean.toString(open));
        save();
    }

    public static boolean moduleBayOpen() {
        return Boolean.parseBoolean(load().getProperty(MODULE_BAY_OPEN, "true"));
    }

    public static void setModuleBayOpen(boolean open) {
        load().setProperty(MODULE_BAY_OPEN, Boolean.toString(open));
        save();
    }

    public static boolean censusOpen() {
        return Boolean.parseBoolean(load().getProperty(CENSUS_OPEN, "true"));
    }

    public static void setCensusOpen(boolean open) {
        load().setProperty(CENSUS_OPEN, Boolean.toString(open));
        save();
    }

    /** Terminal boot flicker on screen open; set false in the file to skip. */
    public static boolean bootAnimation() {
        return Boolean.parseBoolean(load().getProperty(BOOT_ANIMATION, "true"));
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
