package net.gotlicked.LogSpy;

import org.apache.logging.log4j.core.LogEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Tracks repeated log messages and signals when they should be suppressed. */
public final class DedupFilter {

    public enum Result { PASS, SUPPRESS_FIRST, SUPPRESS }

    public static final int THRESHOLD = 3;

    /**
     * All variable parts matched in a single compiled pass.
     * Order matters: UUID and IPv4 must be tried before their component digits.
     * Group 1=UUID  Group 2=IPv4  Group 3=MC-namespace-ID  Group 4=plain-number
     */
    private static final Pattern NORMALIZE = Pattern.compile(
        "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})" // UUID
        + "|(\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b)"                             // IPv4
        + "|([a-z][a-z0-9_]*:[a-z0-9][a-z0-9_./-]*)"                                        // MC namespaced ID
        + "|(\\d+)"                                                                           // plain number
    );

    /** Reusable factory — avoids any lambda-capture overhead on every computeIfAbsent call. */
    private static final Function<String, AtomicInteger> NEW_COUNTER = k -> new AtomicInteger(0);

    /**
     * Permanent cache: raw message text → normalised text.
     * Never reset — normalisation is deterministic and messages recur across the session.
     * Means any message seen once never pays the regex cost again.
     */
    private final ConcurrentHashMap<String, String>        msgNormCache = new ConcurrentHashMap<>();
    /** Normalised key → occurrence count. Cleared on the 60 s reset cycle. */
    private final ConcurrentHashMap<String, AtomicInteger> counts       = new ConcurrentHashMap<>();
    /** Keys for which the one suppression notice has already been emitted. */
    private final Set<String>                              announced    = ConcurrentHashMap.newKeySet();

    /** Returns PASS, SUPPRESS_FIRST (emit one notice), or SUPPRESS (drop silently). */
    public Result check(LogEvent event) {
        String msg  = event.getMessage().getFormattedMessage();
        // computeIfAbsent only runs normalise() the very first time this exact text appears.
        // All subsequent calls — including exact-duplicate spam at 1000/s — pay only a hash lookup.
        String norm = msgNormCache.computeIfAbsent(msg, DedupFilter::normalise);
        String key  = event.getLoggerName() + '|' + event.getLevel() + '|' + norm;

        int n = counts.computeIfAbsent(key, NEW_COUNTER).incrementAndGet();
        if (n <= THRESHOLD)     return Result.PASS;
        if (announced.add(key)) return Result.SUPPRESS_FIRST;
        return Result.SUPPRESS;
    }

    /** Clears occurrence counts and the suppression-notice set. Does NOT clear msgNormCache. */
    public void reset() {
        counts.clear();
        announced.clear();
    }

    /**
     * Replaces all variable parts with stable placeholders using a single regex pass.
     * Four separate replaceAll() calls each compiled the pattern from scratch — this
     * compiles once at class load and traverses the string once per invocation.
     */
    private static String normalise(String msg) {
        return NORMALIZE.matcher(msg).replaceAll(m -> {
            if (m.group(1) != null) return "<uuid>";
            if (m.group(2) != null) return "<ip>";
            if (m.group(3) != null) return "<id>";
            return "#";
        });
    }
}
