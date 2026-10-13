package io.lakio.krios.industry.platform;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.platform.services.IFakePlayers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

public class NeoForgeFakePlayers implements IFakePlayers {

    @Override
    public ServerPlayer get(ServerLevel level) {
        return FakePlayerFactory.get(level, Constants.FAKE_PLAYER_PROFILE);
    }
}
