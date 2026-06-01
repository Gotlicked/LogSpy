package net.gotlicked.logspy;

import net.gotlicked.logspy.services.Services;

public final class LogSpyCommon {
    private LogSpyCommon() {}

    public static void init() {
        LogSpyConstants.LOG.info("Initializing LogSpy on: platform: {}, environment: {}",
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName());
    }
}
