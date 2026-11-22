package io.lakio.krios.core.koios;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What each KOIOS level unlocks, contributed by the content mods at common
 * init (ADR-028): the core terminal lists the current tier's rungs without
 * knowing any content. Display only — the gate itself stays where the
 * content lives (a module's slot insertion, for instance). Plain data, safe
 * on a dedicated server.
 */
public final class KoiosUnlocks {

    /** A rung of {@code tier}'s ladder: {@code nameKey} is unlocked at {@code level}. */
    public record Unlock(int tier, int level, String nameKey) {
    }

    private static final List<Unlock> UNLOCKS = new ArrayList<>();

    private KoiosUnlocks() {
    }

    public static synchronized void register(int tier, int level, String nameKey) {
        UNLOCKS.add(new Unlock(tier, level, nameKey));
    }

    /** The rungs of one tier, by level (ties keep registration order). */
    public static synchronized List<Unlock> ofTier(int tier) {
        List<Unlock> rungs = new ArrayList<>();
        for (Unlock unlock : UNLOCKS) {
            if (unlock.tier() == tier) {
                rungs.add(unlock);
            }
        }
        rungs.sort(Comparator.comparingInt(Unlock::level));
        return rungs;
    }
}
