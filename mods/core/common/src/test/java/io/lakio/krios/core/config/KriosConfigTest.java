package io.lakio.krios.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KriosConfigTest {

    @Test
    void scalingIsBaseTimesPermille() {
        assertEquals(200, KriosConfig.scale(200, 1000));
        assertEquals(300, KriosConfig.scale(200, 1500));
        assertEquals(0, KriosConfig.scale(200, 0));
        assertEquals(66, KriosConfig.scale(200, 333), "integer scaling rounds down");
    }

    @Test
    void scalingDoesNotOverflowOnLargeBases() {
        assertEquals(2_000_000_000, KriosConfig.scale(200_000_000, 10_000));
    }

    @Test
    void withNoServerEveryKeyAnswersItsDefault() {
        for (KriosConfig.Key key : KriosConfig.Key.values()) {
            assertEquals(KriosConfig.DEFAULT_PERMILLE, KriosConfig.permille(null, key), key.id());
        }
    }
}
