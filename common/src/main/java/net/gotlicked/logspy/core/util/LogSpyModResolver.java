package net.gotlicked.logspy.core.util;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Maps logger names to mod IDs using a layered set of strategies:
 *   1. Exact alias match (mod ID, display name, sanitised variants)
 *   2. Case-insensitive alias match
 *   3. Class lookup via ProtectionDomain → JAR URL
 *   4. Java package prefix match (longest wins)
 *   5. Word-boundary substring match against aliases
 *   6. Fallback to a sensible label derived from the logger name itself
 */
public final class LogSpyModResolver {
    private LogSpyModResolver() {}

    // Generic top-level domain segments — never used as a mod label on their own.
    private static final Set<String> GENERIC_TLDS = Set.of(
            "net", "com", "org", "io", "dev", "me", "co", "eu", "xyz");

    private static final ConcurrentHashMap<String, String> urlToModId       = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> packageToModId   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> aliasCaseExact   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> aliasCaseInsens  = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> resolveCache     = new ConcurrentHashMap<>();

    /**
     * Registers everything we know about a mod in one call.
     * Generates name aliases, registers the JAR URL, and scans the JAR (or directory)
     * for actual Java package prefixes used by its classes.
     */
    public static void registerMod(String modId, @Nullable String displayName, @Nullable Path jarPath) {
        if (modId == null || modId.isEmpty()) return;

        addAlias(modId, modId);
        addAlias(modId.replace("-", "").replace("_", ""), modId);

        if (displayName != null && !displayName.isEmpty()) {
            addAlias(displayName, modId);
            addAlias(displayName.replaceAll("\\s+", ""), modId);
        }

        if (jarPath != null) {
            try {
                urlToModId.put(normalizeUrl(jarPath.toUri().toURL().toString()), modId);
            } catch (Exception ignored) {}

            for (String pkg : findPackages(jarPath)) {
                packageToModId.put(pkg, modId);
            }
        }
    }

    /** Clears the resolve-result cache. Call after a batch of new registrations. */
    public static void invalidateCache() {
        resolveCache.clear();
    }

    private static void addAlias(String name, String modId) {
        if (name == null || name.isEmpty()) return;
        aliasCaseExact.put(name, modId);
        aliasCaseInsens.put(name.toLowerCase(Locale.ROOT), modId);
    }

    // ── Resolution ───────────────────────────────────────────────────────────

    /**
     * Resolves a logger name to a label. Never returns null — falls back to
     * a sensible label derived from the logger name itself if nothing else matches.
     */
    public static String resolve(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) return "unknown";
        String cached = resolveCache.get(loggerName);
        if (cached != null) return cached;

        String result = doResolve(loggerName);
        if (result == null) result = deriveFallbackLabel(loggerName);
        resolveCache.put(loggerName, result);
        return result;
    }

    /** Longest registered package prefix matching the given class/logger name. */
    @Nullable
    public static String resolveByPackage(String name) {
        String best = null;
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

    /** Runs strategies 1-5 in order. Returns null only if every strategy fails. */
    @Nullable
    private static String doResolve(String name) {
        String r = aliasCaseExact.get(name);
        if (r != null) return r;

        String lower = name.toLowerCase(Locale.ROOT);
        r = aliasCaseInsens.get(lower);
        if (r != null) return r;

        String base = name;
        int dollar = base.indexOf('$');
        if (dollar > 0) base = base.substring(0, dollar);

        r = resolveViaClassLoader(base);
        if (r != null) return r;

        r = resolveByPackage(base);
        if (r != null) return r;

        r = resolveBySubstring(lower);
        return r;
    }

    /** Loads the class (if it exists) and looks up its JAR URL. */
    @Nullable
    private static String resolveViaClassLoader(String className) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = LogSpyModResolver.class.getClassLoader();
            Class<?> cls = Class.forName(className, false, cl);
            var pd = cls.getProtectionDomain();
            var cs = (pd != null) ? pd.getCodeSource() : null;
            if (cs != null && cs.getLocation() != null) {
                return urlToModId.get(normalizeUrl(cs.getLocation().toString()));
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Looks for any registered alias that appears in the logger name at a word
     * boundary (start of string or after a non-letter-digit character). The
     * word boundary requirement avoids false positives like matching "ae" inside
     * "Resource", while still catching real cases like "Balm" in "BalmReloadCmd".
     */
    @Nullable
    private static String resolveBySubstring(String loggerLower) {
        for (Map.Entry<String, String> entry : aliasCaseInsens.entrySet()) {
            String alias = entry.getKey();
            if (alias.length() < 3) continue;
            int idx = loggerLower.indexOf(alias);
            while (idx >= 0) {
                boolean atBoundary = (idx == 0) ||
                        !Character.isLetterOrDigit(loggerLower.charAt(idx - 1));
                if (atBoundary) return entry.getValue();
                idx = loggerLower.indexOf(alias, idx + 1);
            }
        }
        return null;
    }

    /**
     * Builds a sensible label from a logger name when no mod matched.
     *   "net.minecraft.client.Minecraft"   → "minecraft"
     *   "com.mojang.blaze3d.RenderSystem"  → "mojang"
     *   "FML" / "Balm" / "Modonomicon"     → unchanged
     *   "Tic-Tac-Toe"                      → "Tic-Tac-Toe"
     */
    private static String deriveFallbackLabel(String loggerName) {
        int dot = loggerName.indexOf('.');
        if (dot < 0) return loggerName;

        String[] parts = loggerName.split("\\.");
        String first = parts[0].toLowerCase(Locale.ROOT);
        if (parts.length >= 2 && GENERIC_TLDS.contains(first)) {
            return parts[1];
        }
        return parts[0];
    }

    // ── JAR / directory scanning ─────────────────────────────────────────────

    /** Returns root Java packages observed inside the mod's JAR or class directory. */
    private static Set<String> findPackages(Path path) {
        if (path == null) return Collections.emptySet();
        try {
            Set<String> raw;
            if (Files.isDirectory(path)) {
                raw = scanDirectory(path);
            } else {
                raw = scanJar(path);
            }
            return condenseToRootPackages(raw);
        } catch (Exception _) {
            return Collections.emptySet();
        }
    }

    private static Set<String> scanJar(Path jarPath) throws IOException {
        Set<String> packages = new HashSet<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String n = entries.nextElement().getName();
                if (!n.endsWith(".class") || n.startsWith("META-INF/")) continue;
                int slash = n.lastIndexOf('/');
                if (slash > 0) packages.add(n.substring(0, slash).replace('/', '.'));
            }
        }
        return packages;
    }

    private static Set<String> scanDirectory(Path dir) throws IOException {
        Set<String> packages = new HashSet<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                Path rel = dir.relativize(p);
                Path parent = rel.getParent();
                if (parent != null) {
                    packages.add(parent.toString()
                            .replace('\\', '.')
                            .replace('/',  '.'));
                }
            });
        }
        return packages;
    }

    /**
     * Reduces a flat set of every observed package to just the useful "root"
     * packages — i.e. ones long enough to uniquely identify a mod.
     */
    private static Set<String> condenseToRootPackages(Set<String> all) {
        if (all.isEmpty()) return Collections.emptySet();

        Map<String, List<String>> byFirst = new HashMap<>();
        for (String pkg : all) {
            int dot = pkg.indexOf('.');
            String first = (dot > 0) ? pkg.substring(0, dot) : pkg;
            byFirst.computeIfAbsent(first, k -> new ArrayList<>()).add(pkg);
        }

        Set<String> roots = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : byFirst.entrySet()) {
            String first = entry.getKey();
            String common = longestCommonSegmentPrefix(entry.getValue());

            if (GENERIC_TLDS.contains(first.toLowerCase(Locale.ROOT))) {
                // For generic TLDs we MUST go at least two segments deep to be useful.
                if (common != null && common.indexOf('.') > 0) roots.add(common);
            } else {
                // First segment is already specific enough.
                roots.add(first);
                if (common != null && common.length() > first.length()) roots.add(common);
            }
        }
        return roots;
    }

    /** Longest segment-aligned package prefix shared by every entry in the list. */
    @Nullable
    private static String longestCommonSegmentPrefix(List<String> packages) {
        if (packages.isEmpty()) return null;
        String[] first = packages.getFirst().split("\\.");
        int commonLen = first.length;
        for (String pkg : packages) {
            String[] segments = pkg.split("\\.");
            int max = Math.min(commonLen, segments.length);
            int i = 0;
            while (i < max && segments[i].equals(first[i])) i++;
            commonLen = i;
            if (commonLen == 0) return null;
        }
        return String.join(".", Arrays.copyOfRange(first, 0, commonLen));
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        if (url.startsWith("jar:")) url = url.substring(4);
        int bang = url.indexOf("!/");
        if (bang >= 0) url = url.substring(0, bang);
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }
}
