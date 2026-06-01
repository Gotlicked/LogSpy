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

public final class LogSpyModResolver {
    private LogSpyModResolver() {}

    private static final Set<String> GENERIC_TLDS = Set.of(
            "net", "com", "org", "io", "dev", "me", "co", "eu", "xyz");

    private static final ConcurrentHashMap<String, String> urlToModId      = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> packageToModId  = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> aliasCaseExact  = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> aliasCaseInsens = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> resolveCache    = new ConcurrentHashMap<>();

    // Registers a mod's ID, display name, and JAR path; scans the JAR for package prefixes.
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

    // Clears the resolve cache; call after all mods have been registered.
    public static void invalidateCache() {
        resolveCache.clear();
    }

    // Adds a name to both the case-sensitive and lowercase alias maps.
    private static void addAlias(String name, String modId) {
        if (name == null || name.isEmpty()) return;
        aliasCaseExact.put(name, modId);
        aliasCaseInsens.put(name.toLowerCase(Locale.ROOT), modId);
    }

    // Resolves a logger name to a mod ID label; never returns null, falls back to a derived label.
    public static String resolve(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) return "unknown";
        String cached = resolveCache.get(loggerName);
        if (cached != null) return cached;

        String result = doResolve(loggerName);
        if (result == null) result = deriveFallbackLabel(loggerName);
        resolveCache.put(loggerName, result);
        return result;
    }

    // Returns the mod ID whose registered package prefix is the longest match for the given name.
    @Nullable
    public static String resolveByPackage(String name) {
        String best   = null;
        int bestLen   = 0;
        for (Map.Entry<String, String> entry : packageToModId.entrySet()) {
            String pkg = entry.getKey();
            if (name.startsWith(pkg) && pkg.length() > bestLen) {
                best    = entry.getValue();
                bestLen = pkg.length();
            }
        }
        return best;
    }

    // Tries each resolution strategy in order; returns null only if all five fail.
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

        return resolveBySubstring(lower);
    }

    // Loads the class to read its ProtectionDomain URL, then maps that URL to a mod ID.
    @Nullable
    private static String resolveViaClassLoader(String className) {
        if (className.indexOf('.') < 0) return null;

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

    // Searches all aliases for one that appears at a word boundary within the logger name.
    @Nullable
    private static String resolveBySubstring(String loggerLower) {
        for (Map.Entry<String, String> entry : aliasCaseInsens.entrySet()) {
            String alias = entry.getKey();
            if (alias.length() < 3) continue;
            int idx = loggerLower.indexOf(alias);
            while (idx >= 0) {
                boolean atBoundary = (idx == 0) || !Character.isLetterOrDigit(loggerLower.charAt(idx - 1));
                if (atBoundary) return entry.getValue();
                idx = loggerLower.indexOf(alias, idx + 1);
            }
        }
        return null;
    }

    // Derives a readable label from a logger name when no mod matched, e.g. "net.minecraft.X" → "minecraft".
    private static String deriveFallbackLabel(String loggerName) {
        int dot = loggerName.indexOf('.');
        if (dot < 0) return loggerName;

        String first = loggerName.substring(0, dot);
        if (GENERIC_TLDS.contains(first.toLowerCase(Locale.ROOT))) {
            int nextDot = loggerName.indexOf('.', dot + 1);
            return nextDot < 0
                    ? loggerName.substring(dot + 1)
                    : loggerName.substring(dot + 1, nextDot);
        }
        return first;
    }

    // Returns the root Java packages found in the given JAR or class directory.
    private static Set<String> findPackages(Path path) {
        if (path == null) return Collections.emptySet();
        try {
            Set<String> raw = Files.isDirectory(path) ? scanDirectory(path) : scanJar(path);
            return condenseToRootPackages(raw);
        } catch (Exception ignored) {
            return Collections.emptySet();
        }
    }

    // Enumerates all .class entries in a JAR file and collects their package paths.
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

    // Walks a class directory and collects the package path of every .class file found.
    private static Set<String> scanDirectory(Path dir) throws IOException {
        Set<String> packages = new HashSet<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                Path parent = dir.relativize(p).getParent();
                if (parent != null) {
                    packages.add(parent.toString().replace('\\', '.').replace('/', '.'));
                }
            });
        }
        return packages;
    }

    // Reduces a full set of package paths to just the distinct root prefixes that identify a mod.
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
            String first  = entry.getKey();
            String common = longestCommonSegmentPrefix(entry.getValue());

            if (GENERIC_TLDS.contains(first.toLowerCase(Locale.ROOT))) {
                if (common != null && common.indexOf('.') > 0) roots.add(common);
            } else {
                roots.add(first);
                if (common != null && common.length() > first.length()) roots.add(common);
            }
        }
        return roots;
    }

    // Returns the longest dot-segment prefix shared by all packages in the list.
    @Nullable
    private static String longestCommonSegmentPrefix(List<String> packages) {
        if (packages.isEmpty()) return null;
        String[] first = packages.getFirst().split("\\.");
        int commonLen  = first.length;
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

    // Strips jar: prefixes, !/ suffixes, and trailing slashes from a code-source URL.
    private static String normalizeUrl(String url) {
        if (url == null) return "";
        if (url.startsWith("jar:")) url = url.substring(4);
        int bang = url.indexOf("!/");
        if (bang >= 0) url = url.substring(0, bang);
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }
}
