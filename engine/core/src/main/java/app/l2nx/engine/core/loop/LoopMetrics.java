/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import app.l2nx.engine.core.loop.inbox.Inbox;
import app.l2nx.engine.core.loop.periodic.PeriodicMetrics;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import lombok.Builder;

final class LoopMetrics implements PeriodicMetrics, AutoCloseable {
    private static final String LOOP = "loop";

    private final MeterRegistry registry;
    private final Clock clock;
    private final String loop;
    private final List<Meter> meters = new ArrayList<>();
    private final Timer pass;
    private final Timer[] phases = new Timer[PassPhase.values().length];
    private final Counter[] failures = new Counter[PassPhase.values().length];
    private final Counter overBudget;
    private final Counter overdue;
    private final Counter triggerDepthExceeded;
    private final Counter ownershipViolations;
    private final DistributionSummary messages;
    private final DistributionSummary input;
    private final DistributionSummary timers;
    private final LongSupplier runningPassStart;
    // Written by the owner at the end of a pass; the gauge is read by the scrape thread.
    private volatile long liveTimers;

    LoopMetrics(MeterRegistry registry, String loop, Inbox<?> inbox, LongSupplier runningPassStart) {
        this.registry = registry;
        this.clock = registry.config().clock();
        this.loop = loop;
        this.runningPassStart = runningPassStart;
        pass = add(Timer.builder("l2nx.engine.pass")
                .tag(LOOP, loop)
                .publishPercentiles(0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry));
        for (PassPhase phase : PassPhase.values()) {
            phases[phase.ordinal()] = add(Timer.builder("l2nx.engine.pass.phase")
                    .tag(LOOP, loop)
                    .tag("phase", phase.tag())
                    .register(registry));
            failures[phase.ordinal()] = add(Counter.builder("l2nx.engine.failures")
                    .tag(LOOP, loop)
                    .tag("phase", phase.tag())
                    .register(registry));
        }
        overBudget = add(counter("l2nx.engine.pass.over.budget"));
        overdue = add(counter("l2nx.engine.timers.overdue"));
        triggerDepthExceeded = add(counter("l2nx.engine.trigger.depth.exceeded"));
        ownershipViolations = add(counter("l2nx.engine.ownership.violations"));
        messages = add(items("message"));
        input = add(items("input"));
        timers = add(items("timer"));
        add(Gauge.builder("l2nx.engine.inbox.depth", inbox, Inbox::size)
                .tag(LOOP, loop)
                .register(registry));
        add(Gauge.builder("l2nx.engine.timers", this, metrics -> metrics.liveTimers)
                .tag(LOOP, loop)
                .register(registry));
        // The pass timer records a blocked pass only once it ends; this shows it while it runs.
        add(Gauge.builder("l2nx.engine.pass.running", this, LoopMetrics::runningPassSeconds)
                .tag(LOOP, loop)
                .baseUnit("seconds")
                .register(registry));
    }

    @Override
    public long monotonicTime() {
        return clock.monotonicTime();
    }

    void phase(PassPhase phase, long nanos) {
        phases[phase.ordinal()].record(nanos, TimeUnit.NANOSECONDS);
    }

    void pass(long nanos, int messageCount, int inputCount, int timersFired, long liveTimerCount) {
        pass.record(nanos, TimeUnit.NANOSECONDS);
        messages.record(messageCount);
        input.record(inputCount);
        timers.record(timersFired);
        liveTimers = liveTimerCount;
    }

    void overBudget() {
        overBudget.increment();
    }

    void overdue(int count) {
        if (count > 0) {
            overdue.increment(count);
        }
    }

    void triggerDepthExceeded() {
        triggerDepthExceeded.increment();
    }

    void ownershipViolation() {
        ownershipViolations.increment();
    }

    void failure(PassPhase phase) {
        failures[phase.ordinal()].increment();
    }

    @Override
    public SystemMeters system(String name) {
        return new SystemMeters(
                add(Timer.builder("l2nx.engine.system")
                        .tag(LOOP, loop)
                        .tag("system", name)
                        .register(registry)),
                add(Counter.builder("l2nx.engine.system.skipped")
                        .tag(LOOP, loop)
                        .tag("system", name)
                        .register(registry)));
    }

    private double runningPassSeconds() {
        final long start = runningPassStart.getAsLong();
        return start == Loop.IDLE ? 0 : (clock.monotonicTime() - start) / 1_000_000_000.0;
    }

    static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    // Partitions come and go (instances, matches); their series must not outlive them.
    @Override
    public void close() {
        meters.forEach(registry::remove);
        meters.clear();
    }

    private Counter counter(String name) {
        return Counter.builder(name).tag(LOOP, loop).register(registry);
    }

    private DistributionSummary items(String kind) {
        return DistributionSummary.builder("l2nx.engine.pass.items")
                .tag(LOOP, loop)
                .tag("kind", kind)
                .register(registry);
    }

    private <M extends Meter> M add(M meter) {
        meters.add(meter);
        return meter;
    }

    @Builder(toBuilder = true)
    record SystemMeters(Timer time, Counter skipped) implements PeriodicMetrics.SystemMeters {
        @Override
        public void recordRun(long nanos) {
            time.record(nanos, TimeUnit.NANOSECONDS);
        }

        @Override
        public void recordSkipped(long runs) {
            skipped.increment(runs);
        }
    }
}
