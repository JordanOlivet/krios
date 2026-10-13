package io.lakio.krios.industry;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {

	public static final String MOD_ID = "krios_industry";
	public static final String MOD_NAME = "Krios: Industry";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
	/** The machines' fake player (fixed UUID: kill credit stays stable across restarts). */
	public static final GameProfile FAKE_PLAYER_PROFILE =
			new GameProfile(UUID.fromString("6b72696f-732d-4b4f-494f-532d70646600"), "[Krios]");

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
