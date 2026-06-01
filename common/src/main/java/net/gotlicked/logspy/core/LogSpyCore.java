package net.gotlicked.logspy.core;

import net.gotlicked.logspy.LogSpyConstants;
import net.gotlicked.logspy.core.util.LogSpyAppender;
import net.gotlicked.logspy.core.util.LogSpyDedupFilter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.status.StatusLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

/** Installs the LogSpy pipeline. Call {@link #init()} once from each platform entrypoint. */
public final class LogSpyCore {
    private LogSpyCore() {}

    private static volatile boolean initialised = false;
    private static LogSpyDedupFilter dedup;

    private static final int RESET_INTERVAL_SECONDS = 60;

    /** Installs the log pipeline and exception handler. Idempotent. */
    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        installUncaughtExceptionHandler();
        wirePipeline();
        startResetScheduler();
    }

    /** Replaces all root-logger appenders with a single LogSpyAppender multiplexer. */
    private static void wirePipeline() {
        LoggerContext ctx    = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig  root   = config.getRootLogger();

        Map<String, Level> refLevels = new HashMap<>();
        for (AppenderRef ref : root.getAppenderRefs()) {
            refLevels.put(ref.getRef(), ref.getLevel());
        }

        List<LogSpyAppender.DelegateEntry> delegates = new ArrayList<>();
        for (Appender original : new ArrayList<>(root.getAppenders().values())) {
            delegates.add(new LogSpyAppender.DelegateEntry(original, refLevels.get(original.getName())));
            root.removeAppender(original.getName());
        }

        if (delegates.isEmpty()) {
            StatusLogger.getLogger().warn("No appenders found — pipeline not installed.");
            return;
        }

        dedup = new LogSpyDedupFilter();
        LogSpyAppender spy = new LogSpyAppender("logspyAppender", delegates, dedup);
        spy.start();
        config.addAppender(spy);
        root.addAppender(spy, null, null);
        ctx.updateLoggers();
    }

    /** Installs a global uncaught-exception handler that logs and forwards to the previous handler. */
    private static void installUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (!(throwable instanceof Error)) {
                LogSpyConstants.LOG.error("Uncaught {} on thread '{}'",
                        throwable.getClass().getSimpleName(), thread.getName(), throwable);
            }
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    /** Resets dedup counts every 60 s so messages don't stay suppressed permanently. */
    private static void startResetScheduler() {
            ScheduledExecutorService scheduler =
                    newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "logspyResetSupressed");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(
                    () -> {
                        if (dedup != null) dedup.reset();
                    },
                    RESET_INTERVAL_SECONDS, RESET_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
}
