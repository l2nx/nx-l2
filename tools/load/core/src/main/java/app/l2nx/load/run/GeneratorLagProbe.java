/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.injection.RunTimeline;
import app.l2nx.load.latency.GeneratorLagMonitor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A fixed-rate tick on one event loop through the measured phase; how late each tick runs against its schedule is the
 * generator lag, the method of jHiccup. It does not depend on what load bots do, so the lag is measured even when the
 * server lets no load bot in, and a stalled loop owes every tick it missed. All state is confined to the loop.
 */
final class GeneratorLagProbe {
    static final Duration PERIOD = Duration.ofMillis(1);

    private final EventExecutor loop;
    private final RunTimeline timeline;
    private final GeneratorLagMonitor lag;
    private final long periodNanos;
    private long intended;
    private boolean first = true;
    private boolean done;
    private ScheduledFuture<?> next;

    GeneratorLagProbe(EventExecutor loop, RunTimeline timeline, GeneratorLagMonitor lag) {
        this.loop = loop;
        this.timeline = timeline;
        this.lag = lag;
        periodNanos = PERIOD.toNanos();
    }

    /** The first tick is due at the start of the measured phase. Safe from any thread. */
    void start() {
        loop.execute(() -> {
            intended = timeline.measuredStart();
            schedule();
        });
    }

    /** Stops the ticks; a tick that is due but has not run yet is recorded with the lag it has so far. */
    void close() {
        loop.execute(() -> {
            if (done) {
                return;
            }
            done = true;
            next.cancel(false);
            if (timeline.now() - intended >= 0) {
                record();
            }
        });
    }

    private void schedule() {
        next = loop.schedule(this::tick, Math.max(0, intended - timeline.now()), TimeUnit.NANOSECONDS);
    }

    private void tick() {
        if (done) {
            return;
        }
        if (!record()) {
            done = true;
            return;
        }
        intended += periodNanos;
        schedule();
    }

    /** @return whether the measured phase still holds the tick, so the probe goes on */
    private boolean record() {
        final long lagNanos = timeline.now() - intended;
        // The ramp ends when the last load bot is done arriving; its first tick counts even if that came first.
        final boolean counted = first || timeline.countsLag(intended);
        first = false;
        if (counted) {
            lag.record(lagNanos);
        }
        final int level = timeline.levelAt(intended);
        if (level >= 0) {
            lag.recordLevel(level, lagNanos);
        }
        return counted || intended - timeline.measuredEnd() < 0;
    }
}
