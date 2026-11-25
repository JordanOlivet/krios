package io.lakio.krios.core.koios;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The core's sliding diversity window: the keys of the last
 * {@link KoiosBalance#WINDOW_SIZE} operations in a ring buffer, plus a count
 * per key so a key's share is one lookup. Repetition earns less (the factor
 * falls past the knee), variety earns it back as the window turns over.
 * Plain Java, no world: the block entity owns one and persists its keys.
 */
public final class DiversityWindow {

    private final String[] ring = new String[KoiosBalance.WINDOW_SIZE];
    private int head;
    private int fill;
    private final Map<String, Integer> counts = new HashMap<>();

    /** Records one operation, evicting the oldest once the window is full. */
    public void push(String key) {
        String evicted = ring[head];
        if (evicted == null) {
            fill++;
        } else {
            counts.merge(evicted, -1, (a, b) -> a + b <= 0 ? null : a + b);
        }
        ring[head] = key;
        counts.merge(key, 1, Integer::sum);
        head = (head + 1) % KoiosBalance.WINDOW_SIZE;
    }

    public void clear() {
        Arrays.fill(ring, null);
        counts.clear();
        head = 0;
        fill = 0;
    }

    /** The keys held, oldest first (what is saved and what a dropped core carries). */
    public List<String> keys() {
        List<String> list = new ArrayList<>(fill);
        int start = (head - fill + KoiosBalance.WINDOW_SIZE) % KoiosBalance.WINDOW_SIZE;
        for (int i = 0; i < fill; i++) {
            list.add(ring[(start + i) % KoiosBalance.WINDOW_SIZE]);
        }
        return list;
    }

    /** Operations held, up to the window's size. */
    public int fill() {
        return fill;
    }

    /** Distinct keys held. */
    public int distinct() {
        return counts.size();
    }

    /**
     * The XP factor of one more operation with this key, per-mille: full
     * while the key's share of the window is at most the knee, then linear
     * down to the floor at pure repetition.
     */
    public int factorPermille(String key) {
        if (fill == 0) {
            return 1000;
        }
        int share = counts.getOrDefault(key, 0) * 1000 / fill;
        int knee = KoiosBalance.DIVERSITY_KNEE_PERMILLE;
        int floor = KoiosBalance.DIVERSITY_FLOOR_PERMILLE;
        if (share <= knee) {
            return 1000;
        }
        return Math.max(floor, 1000 - (share - knee) * (1000 - floor) / (1000 - knee));
    }

    /** Distinct operations over fill, per-mille: the GUI's diversity gauge. */
    public int diversityPermille() {
        return fill == 0 ? 1000 : counts.size() * 1000 / fill;
    }
}
