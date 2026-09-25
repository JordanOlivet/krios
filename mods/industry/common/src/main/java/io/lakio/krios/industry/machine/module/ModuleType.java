package io.lakio.krios.industry.machine.module;

import io.lakio.krios.industry.koios.KoiosBalance;

/** The three MVP upgrade modules, each gated behind a KOIOS classic level. */
public enum ModuleType {
    SPEED(KoiosBalance.SPEED_UNLOCK_LEVEL),
    EFFICIENCY(KoiosBalance.EFFICIENCY_UNLOCK_LEVEL),
    YIELD(KoiosBalance.YIELD_UNLOCK_LEVEL);

    private final int requiredLevel;

    ModuleType(int requiredLevel) {
        this.requiredLevel = requiredLevel;
    }

    public int requiredLevel() {
        return requiredLevel;
    }
}
