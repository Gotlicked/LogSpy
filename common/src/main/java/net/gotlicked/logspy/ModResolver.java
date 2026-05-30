package net.gotlicked.logspy;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maps class names and package prefixes to the mod ID that owns them. */
public final class ModResolver {
    private ModResolver() {}

    private static final ConcurrentHashMap<String, String> urlToModId     = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> packageToModId = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> classCache     = new ConcurrentHashMap<>();

    /** Registers the on-disk location (JAR or directory) for a mod. */
    public static void registerUrl(String rawUrl, String modId) {
        urlToModId.put(normalizeUrl(rawUrl), modId);
    }

    /** Registers a Java package prefix for a mod (fallback when URL resolution fails). */
    public static void registerPackage(String packagePrefix, String modId) {
        packageToModId.put(packagePrefix, modId);
    }

    /** Clears the class-name cache so stale entries are re-evaluated after new URLs are registered. */
    public static void invalidateCache() {
        classCache.clear();
    }

    /** Resolves a class name to its owning mod ID, or returns null if unknown. */
    @Nullable
    public static String resolveByClass(String className) {
        if (className == null || className.isEmpty()) return null;

        String cached = classCache.get(className);
        if (cached != null) return "?".equals(cached) ? null : cached;

        String baseName = className;
        int dollar = baseName.indexOf('$');
        if (dollar > 0) baseName = baseName.substring(0, dollar);

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = ModResolver.class.getClassLoader();
            Class<?> cls = Class.forName(baseName, false, cl);
            var pd = cls.getProtectionDomain();
            var cs = (pd != null) ? pd.getCodeSource() : null;
            if (cs != null && cs.getLocation() != null) {
                String key   = normalizeUrl(cs.getLocation().toString());
                String modId = urlToModId.get(key);
                if (modId != null) {
                    classCache.put(className, modId);
                    return modId;
                }
            }
        } catch (ClassNotFoundException | SecurityException ignored) {}

        String fromPkg = resolveByPackage(baseName);
        classCache.put(className, fromPkg != null ? fromPkg : "?");
        return fromPkg;
    }

    /** Finds the longest registered package prefix matching the given name, or null. */
    @Nullable
    public static String resolveByPackage(String name) {
        String best    = null;
        int    bestLen = 0;
        for (Map.Entry<String, String> entry : packageToModId.entrySet()) {
            String pkg = entry.getKey();
            if (name.startsWith(pkg) && pkg.length() > bestLen) {
                best    = entry.getValue();
                bestLen = pkg.length();
            }
        }
        return best;
    }

    /** Strips jar: wrappers, !/ suffixes, and trailing slashes from a code-source URL. */
    private static String normalizeUrl(String url) {
        if (url == null) return "";
        if (url.startsWith("jar:")) url = url.substring(4);
        int bang = url.indexOf("!/");
        if (bang >= 0) url = url.substring(0, bang);
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }
}
