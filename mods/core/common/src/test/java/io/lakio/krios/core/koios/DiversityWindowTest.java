package io.lakio.krios.core.koios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DiversityWindowTest {

    private static final int SIZE = KoiosBalance.WINDOW_SIZE;

    @Test
    void anEmptyWindowPaysInFull() {
        DiversityWindow window = new DiversityWindow();
        assertEquals(1000, window.factorPermille("grind"));
        assertEquals(1000, window.diversityPermille());
        assertEquals(0, window.fill());
    }

    @Test
    void aKeyUnderTheKneePaysInFull() {
        DiversityWindow window = new DiversityWindow();
        // Four keys in turn: each holds a quarter, exactly the knee.
        for (int i = 0; i < SIZE; i++) {
            window.push("op" + (i % 4));
        }
        assertEquals(250, KoiosBalance.DIVERSITY_KNEE_PERMILLE);
        assertEquals(1000, window.factorPermille("op0"));
    }

    @Test
    void pureRepetitionFallsToTheFloor() {
        DiversityWindow window = new DiversityWindow();
        for (int i = 0; i < SIZE; i++) {
            window.push("grind");
        }
        assertEquals(KoiosBalance.DIVERSITY_FLOOR_PERMILLE, window.factorPermille("grind"));
        // A new key still pays in full: variety is rewarded at once.
        assertEquals(1000, window.factorPermille("smelt"));
        assertEquals(1000 / SIZE, window.diversityPermille());
    }

    @Test
    void theFactorFallsLinearlyBetweenKneeAndFloor() {
        DiversityWindow window = new DiversityWindow();
        // Half the window: share 500, halfway between the knee (250) and pure repetition (1000)
        // is a third of the way, so the factor is a third of the way from 1000 to the floor.
        for (int i = 0; i < SIZE; i++) {
            window.push(i % 2 == 0 ? "grind" : "other" + i);
        }
        int knee = KoiosBalance.DIVERSITY_KNEE_PERMILLE;
        int floor = KoiosBalance.DIVERSITY_FLOOR_PERMILLE;
        int expected = 1000 - (500 - knee) * (1000 - floor) / (1000 - knee);
        assertEquals(expected, window.factorPermille("grind"));
        assertTrue(expected < 1000 && expected > floor);
    }

    @Test
    void theOldestOperationLeavesWhenTheWindowIsFull() {
        DiversityWindow window = new DiversityWindow();
        window.push("first");
        for (int i = 0; i < SIZE - 1; i++) {
            window.push("grind");
        }
        assertEquals(SIZE, window.fill());
        assertEquals(2, window.distinct());
        window.push("grind");
        assertEquals(SIZE, window.fill());
        assertEquals(1, window.distinct(), "the first key was evicted");
    }

    @Test
    void keysComeBackOldestFirstAcrossTheWrap() {
        DiversityWindow window = new DiversityWindow();
        List<String> pushed = new ArrayList<>();
        for (int i = 0; i < SIZE + 10; i++) {
            window.push("op" + i);
            pushed.add("op" + i);
        }
        assertEquals(pushed.subList(10, SIZE + 10), window.keys());
    }

    @Test
    void replayingTheSavedKeysRestoresTheWindow() {
        DiversityWindow window = new DiversityWindow();
        for (int i = 0; i < SIZE + 5; i++) {
            window.push(i % 3 == 0 ? "grind" : "smelt");
        }
        DiversityWindow restored = new DiversityWindow();
        for (String key : window.keys()) {
            restored.push(key);
        }
        assertEquals(window.keys(), restored.keys());
        assertEquals(window.factorPermille("grind"), restored.factorPermille("grind"));
        assertEquals(window.diversityPermille(), restored.diversityPermille());
    }

    @Test
    void clearEmptiesEverything() {
        DiversityWindow window = new DiversityWindow();
        window.push("grind");
        window.clear();
        assertEquals(0, window.fill());
        assertEquals(0, window.distinct());
        assertTrue(window.keys().isEmpty());
    }
}
