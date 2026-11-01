/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.fixture;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/** A scheduler whose one-shot tasks run only when a test says so; its clock stands still at the epoch. */
public final class ManualScheduler implements TaskScheduler {
    private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Duration> delays = new ArrayList<>();

    public synchronized List<Duration> delays() {
        return List.copyOf(delays);
    }

    /** Runs the tasks due now, not the ones they schedule in turn. */
    public void runDue() {
        final List<Runnable> due;
        synchronized (this) {
            due = List.copyOf(tasks);
            tasks.clear();
        }
        due.forEach(Runnable::run);
    }

    @Override
    public Clock getClock() {
        return CLOCK;
    }

    @Override
    public synchronized ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        tasks.add(task);
        delays.add(Duration.between(Instant.EPOCH, startTime));
        return null;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        throw new UnsupportedOperationException();
    }
}
