package io.lakio.krios.industry.platform;

import io.lakio.krios.core.platform.Services;
import io.lakio.krios.industry.platform.services.IFakePlayers;
import io.lakio.krios.industry.platform.services.IItemAccess;

/**
 * Industry's own platform services (item exchange with passive neighbors,
 * the machines' fake player). Registration and platform queries are core's
 * {@link Services}; the lookup is the same ServiceLoader mechanism.
 */
public class IndustryServices {

    public static final IItemAccess ITEMS = Services.load(IItemAccess.class);
    public static final IFakePlayers FAKE_PLAYERS = Services.load(IFakePlayers.class);
}
