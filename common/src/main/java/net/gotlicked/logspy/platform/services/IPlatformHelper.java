package net.gotlicked.logspy.platform.services;

public interface IPlatformHelper {

    /** Returns the name of the current mod loader platform. */
    String getPlatformName();

    /** Returns true if the given mod ID is currently loaded. */
    boolean isModLoaded(String modId);

    /** Returns true when running inside a development environment. */
    boolean isDevelopmentEnvironment();

    /** Returns "development" or "production". */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
