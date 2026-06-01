package net.gotlicked.logspy;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.gotlicked.logspy.core.LogSpyCore;

public final class FabricPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        LogSpyCore.init();
    }
}
