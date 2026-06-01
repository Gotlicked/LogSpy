package net.gotlicked.logspy.platform;

import net.gotlicked.logspy.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatformHelper implements IPlatformHelper {

    // Returns "Fabric".
    @Override
    public String getPlatformName() { return "Fabric"; }

    // Returns true if the given mod ID is loaded by Fabric.
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    // Returns true when Fabric is running in a development environment.
    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
