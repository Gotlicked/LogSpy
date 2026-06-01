package net.gotlicked.logspy;

import net.gotlicked.logspy.core.LogSpyCore;
import net.gotlicked.logspy.core.util.LogSpyModResolver;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

import java.nio.file.Path;

@Mod(LogSpyConstants.MOD_ID)
public final class LogSpyNeoforge {

    // Installs the pipeline and registers every loaded mod with the resolver.
    public LogSpyNeoforge(IEventBus eventBus) {
        LogSpyCore.init();
        ModList.get().getMods().parallelStream().forEach(info -> {
            String modId       = info.getModId();
            String displayName = info.getDisplayName();
            Path   jarPath     = safeFilePath(info);
            LogSpyModResolver.registerMod(modId, displayName, jarPath);
        });
        LogSpyModResolver.invalidateCache();
        LogSpyCommon.init();
    }

    // Returns the JAR file path for a mod, or null if it cannot be read.
    private static Path safeFilePath(net.neoforged.neoforgespi.language.IModInfo info) {
        try {
            return info.getOwningFile().getFile().getFilePath();
        } catch (Exception ignored) {
            return null;
        }
    }
}
