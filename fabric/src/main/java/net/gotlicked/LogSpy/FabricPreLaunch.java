package net.gotlicked.LogSpy;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Fires before any ModInitializer, installing the LogSpy pipeline as early as possible. */
public final class FabricPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        LogSpyCore.init();
    }
}
