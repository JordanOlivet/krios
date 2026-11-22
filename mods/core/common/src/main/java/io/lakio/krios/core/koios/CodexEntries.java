package io.lakio.krios.core.koios;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Codex's table of contents, contributed by every Krios mod at common
 * init (ADR-028). An entry is a lang key base: the Codex reads
 * {@code <key>.title}, {@code <key>.body} (and {@code .body.t<N>},
 * {@code .adv}, {@code .projection.t<N>} when they exist), so each mod keeps
 * its entries' text in its own lang files. Plain data, safe on a dedicated
 * server; the Codex screen reads it client-side.
 *
 * <p>{@code order} places an entry in the sidebar: core's opening entries
 * sit under 100 and its closing ones from 800, content mods take a band in
 * between (Industry: 100-299). Ties keep registration order.
 */
public final class CodexEntries {

    /** minLevel gates the entry; hasAdvanced adds the {@code .adv} paragraph. */
    public record Entry(int order, String key, int minLevel, boolean hasAdvanced) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static List<Entry> sorted;

    private CodexEntries() {
    }

    public static synchronized void register(int order, String key, int minLevel, boolean hasAdvanced) {
        ENTRIES.add(new Entry(order, key, minLevel, hasAdvanced));
        sorted = null;
    }

    /** Every entry, in sidebar order. */
    public static synchronized List<Entry> all() {
        if (sorted == null) {
            List<Entry> copy = new ArrayList<>(ENTRIES);
            copy.sort(Comparator.comparingInt(Entry::order));
            sorted = List.copyOf(copy);
        }
        return sorted;
    }
}
