package net.gotlicked.logspy.platform;

import net.gotlicked.logspy.Constants;
import net.gotlicked.logspy.platform.services.IPlatformHelper;
import java.util.ServiceLoader;

/** Loads platform-specific service implementations via Java's ServiceLoader. */
public final class Services {
    private Services() {}

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    /** Loads the first available implementation of the given service interface. */
    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
