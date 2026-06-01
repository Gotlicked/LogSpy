package net.gotlicked.logspy.services;

import net.gotlicked.logspy.LogSpyConstants;

import java.util.ServiceLoader;

public final class Services {
    private Services() {}

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    // Loads the first available implementation of the given service interface via ServiceLoader.
    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        LogSpyConstants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
