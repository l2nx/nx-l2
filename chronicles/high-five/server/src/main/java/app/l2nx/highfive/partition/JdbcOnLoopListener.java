/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.logging.RateLimitedLog;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.listener.MethodExecutionContext;
import net.ttddyy.dsproxy.listener.MethodExecutionListener;

/** Times JDBC calls of loop threads into {@code l2nx.engine.jdbc.on.loop{loop}}; warns once per call site and minute. */
// TODO: drop once E4 moves JDBC off the loop (#69); then enable the ArchUnit rule against JDBC on loop threads.
@Slf4j
final class JdbcOnLoopListener implements MethodExecutionListener {
    static final String METER = "l2nx.engine.jdbc.on.loop";

    private static final String LOOP = "loop";
    private static final String STARTED = JdbcOnLoopListener.class.getName();
    private static final String EXECUTE = "execute";
    private static final Duration WARNING_INTERVAL = Duration.ofMinutes(1);
    private static final StackWalker CALLERS = StackWalker.getInstance();
    // The repositories are skipped too: the site worth moving off the loop is the game code that asked for the data.
    private static final List<String> PLUMBING = List.of(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "com.sun.",
            "net.ttddyy.",
            "com.zaxxer.",
            "org.postgresql.",
            "org.springframework.",
            "app.l2nx.highfive.repository.");

    private final MeterRegistry registry;
    private final Meter.MeterProvider<Timer> timers;
    private final RateLimitedLog warnings;

    JdbcOnLoopListener(MeterRegistry registry) {
        this.registry = registry;
        this.timers = Timer.builder(METER)
                .description("Time loop threads spent in JDBC calls")
                .withRegistry(registry);
        this.warnings = new RateLimitedLog(WARNING_INTERVAL, registry.config().clock()::monotonicTime);
    }

    @Override
    public void beforeMethod(MethodExecutionContext context) {
        final Loop loop = Loop.current();
        if (loop != null) {
            context.addCustomValue(STARTED, new Started(loop.getName(), Timer.start(registry)));
        }
    }

    @Override
    public void afterMethod(MethodExecutionContext context) {
        final Started started = context.getCustomValue(STARTED, Started.class);
        if (started == null) {
            return;
        }

        final long nanos = started.sample().stop(timers.withTag(LOOP, started.loop()));
        if (context.getTarget() instanceof Statement
                && context.getMethod().getName().startsWith(EXECUTE)) {
            callSite()
                    .filter(warnings::tryAcquire)
                    .ifPresent(site -> log.atWarn()
                            .addKeyValue(LOOP, started.loop())
                            .addKeyValue("site", site)
                            .log(
                                    "JDBC statement ran on loop {} for {} us at {}",
                                    started.loop(),
                                    TimeUnit.NANOSECONDS.toMicros(nanos),
                                    site));
        }
    }

    private static Optional<String> callSite() {
        return CALLERS.walk(frames -> frames.filter(frame -> !isPlumbing(frame.getClassName()))
                .findFirst()
                .map(frame -> frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber()));
    }

    private static boolean isPlumbing(String className) {
        if (className.equals(JdbcOnLoopListener.class.getName())) {
            return true;
        }
        for (String prefix : PLUMBING) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private record Started(String loop, Timer.Sample sample) {}
}
