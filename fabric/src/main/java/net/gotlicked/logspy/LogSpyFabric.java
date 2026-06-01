package net.gotlicked.logspy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.gotlicked.logspy.core.util.LogSpyModResolver;

import java.nio.file.Path;

public final class LogSpyFabric implements ModInitializer {

    // Registers every loaded mod with the resolver in parallel, then invalidates the cache.
    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getAllMods().parallelStream().forEach(container -> {
            String modId       = container.getMetadata().getId();
            String displayName = container.getMetadata().getName();
            Path   jarPath     = container.getRootPaths().stream().findFirst().orElse(null);
            LogSpyModResolver.registerMod(modId, displayName, jarPath);
        });
        LogSpyModResolver.invalidateCache();
        LogSpyCommon.init();
    }
}
