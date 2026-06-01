package net.gotlicked.logspy.core.util;

import net.gotlicked.logspy.LogSpyConstants;
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

public final class LogSpyAppender extends AbstractAppender {

    public record DelegateEntry(Appender appender, @Nullable Level minLevel) {}

    private final DelegateEntry[] delegates;
    private final LogSpyDedupFilter dedup;
    private volatile boolean lastWasSuppression = false;

    // Converts the delegate list to an array so the appended loop uses a plain indexed iteration.
    public LogSpyAppender(String name, List<DelegateEntry> delegates, LogSpyDedupFilter dedup) {
        super(name, null, null, true, Property.EMPTY_ARRAY);
        this.delegates = delegates.toArray(new DelegateEntry[0]);
        this.dedup     = dedup;
    }

    // Runs dedup, gates consecutive suppression notices, tags the event with a mod ID, and forwards it.
    @Override
    public void append(LogEvent event) {
        LogSpyDedupFilter.Result result = dedup.check(event);
        if (result == LogSpyDedupFilter.Result.SUPPRESS) return;

        if (result == LogSpyDedupFilter.Result.SUPPRESS_FIRST) {
            if (lastWasSuppression) return;
            lastWasSuppression = true;
        } else {
            lastWasSuppression = false;
        }

        boolean isSuppression = (result == LogSpyDedupFilter.Result.SUPPRESS_FIRST);
        String  originModId   = resolveModId(event);
        String  modId         = isSuppression ? LogSpyConstants.MOD_ID : originModId;

        String body = isSuppression
                ? "Log spam detected: Intercepted and silenced multiple similar [" + event.getLevel().toString() + "] messages from: [" + originModId + "]."
                : event.getMessage().getFormattedMessage();

        Throwable thrown = isSuppression ? null : event.getThrown();
        LogEvent  out    = new TaggedEvent(event, new SimpleMessage("[" + modId + "] " + body), thrown);

        Level outLevel = out.getLevel();
        for (DelegateEntry entry : delegates) {
            Level min = entry.minLevel();
            if (min == null || outLevel.isMoreSpecificThan(min)) {
                entry.appender().append(out);
            }
        }
    }

    // Resolves the event's logger name to a mod ID string using LogSpyModResolver.
    private static String resolveModId(LogEvent event) {
        return LogSpyModResolver.resolve(event.getLoggerName());
    }

    // Wraps a LogEvent, overriding only the message and throwable; all other fields delegate to the source.
    private record TaggedEvent(LogEvent src, Message msg, Throwable thrown) implements LogEvent {

        @Serial
        private static final long serialVersionUID = 1L;

        // Returns the overridden message.
        @Override public Message getMessage() { return msg; }

        // Returns the overridden throwable (null for suppression notices).
        @Override public Throwable getThrown() { return thrown; }

        // Returns the throwable proxy only when our thrown matches the source throwable.
        @SuppressWarnings("deprecation")
        @Override public ThrowableProxy getThrownProxy() {
            return (thrown != null && thrown == src.getThrown()) ? src.getThrownProxy() : null;
        }

        // Always returns null; source location is not captured without includeLocation="true".
        @Override public StackTraceElement getSource()          { return null; }

        // Always returns false; location capture is disabled.
        @Override public boolean isIncludeLocation()            { return false; }
        @Override public void    setIncludeLocation(boolean b)  {}

        @Override public Level   getLevel()         { return src.getLevel(); }
        @Override public String  getLoggerName()    { return src.getLoggerName(); }
        @Override public Marker  getMarker()        { return src.getMarker(); }
        @Override public String  getLoggerFqcn()    { return src.getLoggerFqcn(); }
        @Override public ReadOnlyStringMap getContextData()              { return src.getContextData(); }
        @Override public ThreadContext.ContextStack getContextStack()    { return src.getContextStack(); }
        @Override public String  getThreadName()    { return src.getThreadName(); }
        @Override public long    getThreadId()      { return src.getThreadId(); }
        @Override public int     getThreadPriority(){ return src.getThreadPriority(); }
        @Override public long    getTimeMillis()    { return src.getTimeMillis(); }
        @Override public Instant getInstant()       { return src.getInstant(); }
        @Override public long    getNanoTime()      { return src.getNanoTime(); }
        @Override public boolean isEndOfBatch()     { return src.isEndOfBatch(); }
        @Override public void    setEndOfBatch(boolean b) {}
        @Override public LogEvent toImmutable()     { return this; }

        @SuppressWarnings("deprecation")
        @Override public Map<String, String> getContextMap() { return src.getContextMap(); }
    }
}
