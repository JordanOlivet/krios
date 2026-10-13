package io.lakio.krios.industry.platform;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.platform.services.IFakePlayers;
import io.lakio.krios.industry.platform.services.IItemAccess;
import io.lakio.krios.industry.platform.services.IPlatformHelper;
import io.lakio.krios.industry.platform.services.IRegistrar;

import java.util.ServiceLoader;

// Service loaders locate the platform-specific implementation of each common
// interface. Implementations are declared in META-INF/services files inside
// the fabric and neoforge source sets.
public class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final IRegistrar REGISTRAR = load(IRegistrar.class);
    public static final IItemAccess ITEMS = load(IItemAccess.class);
    public static final IFakePlayers FAKE_PLAYERS = load(IFakePlayers.class);

    public static <T> T load(Class<T> clazz) {

        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
