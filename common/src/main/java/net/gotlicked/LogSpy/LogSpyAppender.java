package net.gotlicked.LogSpy;

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
    private final DedupFilter         dedup;
    private volatile boolean lastWasSuppression = false;

    public LogSpyAppender(String name, List<DelegateEntry> delegates, DedupFilter dedup) {
        super(name, null, null, true, Property.EMPTY_ARRAY);
        this.delegates = delegates;
        this.dedup     = dedup;
    }

    @Override
    public void append(LogEvent event) {
        DedupFilter.Result result = dedup.check(event);
        if (result == DedupFilter.Result.SUPPRESS) return;

        // Don't let "Silenced multiple Xs of this type" get printed multiple times in a row.
        if (result == DedupFilter.Result.SUPPRESS_FIRST) {
            if (lastWasSuppression) return;
            lastWasSuppression = true;
        } else {
            // A real message is going through — reset the gate.
            lastWasSuppression = false;
        }

        String  modId         = resolveModId(event);
        boolean isSuppression = (result == DedupFilter.Result.SUPPRESS_FIRST);

        String body = isSuppression
                ? "Silenced multiple " + event.getLevel().toString().toLowerCase() + "s of this type"
                : event.getMessage().getFormattedMessage();

        // TaggedEvent wraps the original rather than copying all its fields into a new Log4jLogEvent.
        // Allocates 3 fields instead of a full builder + 10+ field LogEvent object.
        Throwable thrown = isSuppression ? null : event.getThrown();
        LogEvent  out    = new TaggedEvent(event, new SimpleMessage("[" + modId + "] " + body), thrown);

        for (DelegateEntry entry : delegates) {
            if (entry.minLevel() == null || out.getLevel().isMoreSpecificThan(entry.minLevel())) {
                entry.appender().append(out);
            }
        }
    }

    /**
     * Resolves the logger name to its owning mod ID.
     * event.getSource() is intentionally skipped — it always returns null unless
     * the log4j config has includeLocation="true", which it doesn't.
     * resolveByClass() already falls back to resolveByPackage() internally,
     * so a second explicit call is redundant.
     */
    private static String resolveModId(LogEvent event) {
        String logger = event.getLoggerName();
        if (logger != null && !logger.isEmpty()) {
            String r = ModResolver.resolveByClass(logger);
            if (r != null) return r;
        }
        return "unknown";
    }

    // ── TaggedEvent ───────────────────────────────────────────────────────────

    /**
     * Lightweight LogEvent wrapper that overrides only the message (and optionally the
     * throwable). Everything else delegates to the original event with zero field copying.
     */
    private static final class TaggedEvent implements LogEvent {

        private static final long serialVersionUID = 1L;

        private final LogEvent  src;
        private final Message   msg;
        private final Throwable thrown;

        TaggedEvent(LogEvent src, Message msg, Throwable thrown) {
            this.src    = src;
            this.msg    = msg;
            this.thrown = thrown;
        }

        @Override public Message   getMessage()     { return msg; }
        @Override public Throwable getThrown()      { return thrown; }

        /** Only return  the proxy when our thrown is the same object as the original's. */
        @Override public ThrowableProxy getThrownProxy() {
            return (thrown != null && thrown == src.getThrown()) ? src.getThrownProxy() : null;
        }

        /** Source location is never populated without includeLocation="true". */
        @Override public StackTraceElement getSource()      { return null; }
        @Override public boolean           isIncludeLocation() { return false; }
        @Override public void              setIncludeLocation(boolean b) {}

        @Override public Level             getLevel()       { return src.getLevel(); }
        @Override public String            getLoggerName()  { return src.getLoggerName(); }
        @Override public Marker            getMarker()      { return src.getMarker(); }
        @Override public String            getLoggerFqcn()  { return src.getLoggerFqcn(); }
        @Override public ReadOnlyStringMap getContextData() { return src.getContextData(); }
        @Override public ThreadContext.ContextStack getContextStack() { return src.getContextStack(); }
        @Override public String            getThreadName()  { return src.getThreadName(); }
        @Override public long              getThreadId()    { return src.getThreadId(); }
        @Override public int               getThreadPriority() { return src.getThreadPriority(); }
        @Override public long              getTimeMillis()  { return src.getTimeMillis(); }
        @Override public Instant           getInstant()     { return src.getInstant(); }
        @Override public long              getNanoTime()    { return src.getNanoTime(); }
        @Override public boolean           isEndOfBatch()   { return src.isEndOfBatch(); }
        @Override public void              setEndOfBatch(boolean b) {}
        @Override public LogEvent          toImmutable()    { return this; }

        @SuppressWarnings("deprecation")
        @Override public Map<String, String> getContextMap() { return src.getContextMap(); }
    }
}
