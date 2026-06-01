package net.gotlicked.logspy.platform;

import net.gotlicked.logspy.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public class NeoForgePlatformHelper implements IPlatformHelper {

    // Returns "NeoForge".
    @Override
    public String getPlatformName() { return "NeoForge"; }

    // Returns true if the given mod ID is loaded by NeoForge.
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    // Returns true when FML is not running in production mode.
    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }
}
