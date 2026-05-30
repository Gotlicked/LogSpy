package net.gotlicked.LogSpy;

import net.gotlicked.LogSpy.platform.Services;

/** Shared init logic called by both platform entrypoints after the log pipeline is ready. */
public final class CommonClass {
    private CommonClass() {}

    public static void init() {
        Constants.LOG.info("LogSpy active — platform: {}, environment: {}",
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName());
    }
}
