package net.gotlicked.logspy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric main entrypoint. Registers mod JAR locations for log attribution. */
public final class LogSpyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getAllMods().forEach(container -> {
            String modId = container.getMetadata().getId();
            container.getRootPaths().forEach(path -> {
                try {
                    ModResolver.registerUrl(path.toUri().toURL().toString(), modId);
                } catch (Exception ignored) {}
            });
            ModResolver.registerPackage(sanitiseId(modId), modId);
        });
        ModResolver.invalidateCache();
        CommonClass.init();
    }

    /** Strips hyphens and underscores so a mod ID maps to a valid Java package prefix. */
    private static String sanitiseId(String modId) {
        return modId.replace("-", "").replace("_", "");
    }
}
