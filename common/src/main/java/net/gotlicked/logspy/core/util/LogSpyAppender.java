package net.gotlicked.logspy.core.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.List;
import java.util.Map;

/**
 * Single root-logger multiplexer that handles dedup and mod-ID tagging once per event,
 * then routes to each original appender respecting its configured level.
 */
public final class LogSpyAppender extends AbstractAppender {

    /** Pairs an original appender with its configured minimum log level (null = inherit root). */
    public record DelegateEntry(Appender appender, @Nullable Level minLevel) {}

    private final List<DelegateEntry> delegates;
    private final LogSpyDedupFilter dedup;

    /**
     * True when the last event actually forwarded to delegates was a suppression notice.
     * Used to prevent consecutive "Silenced multiple…" lines stacking with no real message
     * in between. Volatile — correct across threads without full synchronisation.
     */
    private volatile boolean lastWasSuppression = false;

    public LogSpyAppender(String name, List<DelegateEntry> delegates, LogSpyDedupFilter dedup) {
        super(name, null, null, true, Property.EMPTY_ARRAY);
        this.delegates = delegates;
        this.dedup     = dedup;
    }

    @Override
    public void append(LogEvent event) {
        LogSpyDedupFilter.Result result = dedup.check(event);
        if (result == LogSpyDedupFilter.Result.SUPPRESS) return;

        // Gate: if the last thing printed was already a suppression notice, swallow any
        // further notices silently until at least one real PASS message has gone through.
        if (result == LogSpyDedupFilter.Result.SUPPRESS_FIRST) {
            if (lastWasSuppression) return;
            lastWasSuppression = true;
        } else {
            lastWasSuppression = false;
        }

        String  modId         = resolveModId(event);
        boolean isSuppression = (result == LogSpyDedupFilter.Result.SUPPRESS_FIRST);

        String body = isSuppression
                ? "Silenced" + event.getLevel().toString()
                : event.getMessage().getFormattedMessage();

        Throwable thrown = isSuppression ? null : event.getThrown();
        LogEvent  out    = new TaggedEvent(event, new SimpleMessage("[" + modId + "] " + body), thrown);

        for (DelegateEntry entry : delegates) {
            if (entry.minLevel() == null || out.getLevel().isMoreSpecificThan(entry.minLevel())) {
                entry.appender().append(out);
            }
        }
    }

    /**
     * Resolves the logger name to a mod ID. Delegates to ModResolver which now
     * tries multiple strategies (aliases, class lookup, package prefix, substring)
     * and falls back to a label derived from the logger name itself rather than
     * ever returning "unknown".
     */
    private static String resolveModId(LogEvent event) {
        return LogSpyModResolver.resolve(event.getLoggerName());
    }

    // ── TaggedEvent ───────────────────────────────────────────────────────────

    /**
         * Lightweight LogEvent wrapper — overrides only message and thrown.
         * Everything else delegates to the original with zero field copying.
         */
        private record TaggedEvent(LogEvent src, Message msg, Throwable thrown) implements LogEvent {

            @Serial
            private static final long serialVersionUID = 1L;

        @Override
        public Message getMessage() {
            return msg;
        }

        @Override
        public Throwable getThrown() {
            return thrown;
        }

            @SuppressWarnings("deprecation")
            @Override
            public ThrowableProxy getThrownProxy() {
                return (thrown != null && thrown == src.getThrown()) ? src.getThrownProxy() : null;
            }

            @Override
            public StackTraceElement getSource() {
                return null;
            }

        @Override
        public boolean isIncludeLocation() {
            return false;
        }

        @Override
        public void setIncludeLocation(boolean b) {
        }

            @Override
            public Level getLevel() {
                return src.getLevel();
            }

        @Override
        public String getLoggerName() {
            return src.getLoggerName();
        }

        @Override
        public Marker getMarker() {
            return src.getMarker();
        }

        @Override
        public String getLoggerFqcn() {
            return src.getLoggerFqcn();
        }

        @Override
        public ReadOnlyStringMap getContextData() {
            return src.getContextData();
        }

        @Override
        public ThreadContext.ContextStack getContextStack() {
            return src.getContextStack();
        }

        @Override
        public String getThreadName() {
            return src.getThreadName();
        }

        @Override
        public long getThreadId() {
            return src.getThreadId();
        }

        @Override
        public int getThreadPriority() {
            return src.getThreadPriority();
        }

        @Override
        public long getTimeMillis() {
            return src.getTimeMillis();
        }

        @Override
        public Instant getInstant() {
            return src.getInstant();
        }

        @Override
        public long getNanoTime() {
            return src.getNanoTime();
        }

        @Override
        public boolean isEndOfBatch() {
            return src.isEndOfBatch();
        }

        @Override
        public void setEndOfBatch(boolean b) {
        }

        @Override
        public LogEvent toImmutable() {
            return this;
        }

            @SuppressWarnings("deprecation")
            @Override
            public Map<String, String> getContextMap() {
                return src.getContextMap();
            }
        }
}
