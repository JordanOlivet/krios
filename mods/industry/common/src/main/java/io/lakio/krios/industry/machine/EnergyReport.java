package io.lakio.krios.industry.machine;

import io.lakio.krios.industry.network.Network;
import io.lakio.krios.industry.network.NetworkNode;

/** Network-wide energy sums over members implementing {@link EnergyStats}. */
public final class EnergyReport {

    private EnergyReport() {
    }

    public static int supply(Network network) {
        int total = 0;
        for (NetworkNode member : network.members()) {
            if (member instanceof EnergyStats stats) {
                total += stats.energySupplyPerTick();
            }
        }
        return total;
    }

    public static int draw(Network network) {
        int total = 0;
        for (NetworkNode member : network.members()) {
            if (member instanceof EnergyStats stats) {
                total += stats.energyDrawPerTick();
            }
        }
        return total;
    }

    public static int consumed(Network network) {
        int total = 0;
        for (NetworkNode member : network.members()) {
            if (member instanceof EnergyStats stats) {
                total += stats.energyConsumedPerTick();
            }
        }
        return total;
    }

    public static int starvedCount(Network network) {
        int count = 0;
        for (NetworkNode member : network.members()) {
            if (member instanceof EnergyStats stats && stats.energyStarved()) {
                count++;
            }
        }
        return count;
    }

    public static int stored(Network network) {
        int total = 0;
        for (NetworkNode member : network.members()) {
            if (member instanceof EnergyStats stats) {
                total += stats.energyStored();
            }
        }
        return total;
    }

    public static int capacity(Network network) {
        int total = 0;
        for (NetworkNode member : network.members()) {
            if (member instanceof EnergyStats stats) {
                total += stats.energyCapacity();
            }
        }
        return total;
    }
}
