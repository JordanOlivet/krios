package io.lakio.krios.industry.platform.services;

import net.minecraft.world.item.ItemStack;

public interface IPlatformHelper {

    /**
     * Furnace-fuel burn time in ticks, 0 when the stack is not a fuel.
     * Loader-specific: NeoForge extends fuels per-stack, Fabric feeds the
     * vanilla fuel map.
     */
    int getBurnTime(ItemStack stack);

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {

        return isDevelopmentEnvironment() ? "development" : "production";
    }
}