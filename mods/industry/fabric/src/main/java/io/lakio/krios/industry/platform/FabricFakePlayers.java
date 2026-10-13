package io.lakio.krios.industry.platform;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.platform.services.IFakePlayers;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class FabricFakePlayers implements IFakePlayers {

    @Override
    public ServerPlayer get(ServerLevel level) {
        return FakePlayer.get(level, Constants.FAKE_PLAYER_PROFILE);
    }
}
