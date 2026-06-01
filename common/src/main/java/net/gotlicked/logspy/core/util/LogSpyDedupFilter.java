package net.gotlicked.logspy.core.util;

import org.apache.logging.log4j.core.LogEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class LogSpyDedupFilter {

    public enum Result { PASS, SUPPRESS_FIRST, SUPPRESS }

    public static final int THRESHOLD = 3;

    private static final Pattern NORMALIZE = Pattern.compile(
        "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
        + "|(\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b)"
        + "|([a-z][a-z0-9_]*:[a-z0-9][a-z0-9_./-]*)"
        + "|(\\d+)"
    );

    private static final Function<String, AtomicInteger> NEW_COUNTER = k -> new AtomicInteger(0);

    private final ConcurrentHashMap<String, String>        msgNormCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> counts       = new ConcurrentHashMap<>();
    private final Set<String>                              announced    = ConcurrentHashMap.newKeySet();

    // Normalises the message, increments its counter, and returns PASS, SUPPRESS_FIRST, or SUPPRESS.
    public Result check(LogEvent event) {
        String msg  = event.getMessage().getFormattedMessage();
        String norm = msgNormCache.computeIfAbsent(msg, LogSpyDedupFilter::normalise);
        String key  = event.getLoggerName() + '|' + event.getLevel() + '|' + norm;

        int n = counts.computeIfAbsent(key, NEW_COUNTER).incrementAndGet();
        if (n <= THRESHOLD)     return Result.PASS;
        if (announced.add(key)) return Result.SUPPRESS_FIRST;
        return Result.SUPPRESS;
    }

    // Clears occurrence counts and the announced set; does not clear the normalisation cache.
    public void reset() {
        counts.clear();
        announced.clear();
    }

    // Replaces UUIDs, IPs, Minecraft namespaced IDs, and plain numbers with fixed placeholders.
    private static String normalise(String msg) {
        return NORMALIZE.matcher(msg).replaceAll(m -> {
            if (m.group(1) != null) return "<uuid>";
            if (m.group(2) != null) return "<ip>";
            if (m.group(3) != null) return "<id>";
            return "#";
        });
    }
}
