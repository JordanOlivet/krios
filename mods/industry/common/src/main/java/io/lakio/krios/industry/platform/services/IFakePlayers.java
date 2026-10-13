package io.lakio.krios.industry.platform.services;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The machines' stand-in player, through each loader's own fake-player
 * API (NeoForge {@code FakePlayerFactory}, Fabric API {@code FakePlayer}).
 * Attacks made with it count as player kills: XP orbs and player-only
 * loot apply as for a real player (Phase Death Field, ADR-026).
 */
public interface IFakePlayers {

    /** The shared Krios fake player of this level (one per level, cached by the loader). */
    ServerPlayer get(ServerLevel level);
}
