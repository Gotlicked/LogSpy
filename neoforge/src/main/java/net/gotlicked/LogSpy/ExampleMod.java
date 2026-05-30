package net.gotlicked.LogSpy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

/** NeoForge @Mod entrypoint. Installs the pipeline and registers mod JAR locations. */
@Mod(Constants.MOD_ID)
public final class ExampleMod {

    public ExampleMod(IEventBus eventBus) {
        LogSpyCore.init();

        ModList.get().getMods().forEach(info -> {
            String modId = info.getModId();
            try {
                var filePath = info.getOwningFile().getFile().getFilePath();
                ModResolver.registerUrl(filePath.toUri().toURL().toString(), modId);
            } catch (Exception ignored) {}
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
