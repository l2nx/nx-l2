/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/** Users declare {@code @Execution(SAME_THREAD)} and {@code @ResourceLock(LOCK)}: the bridge is static. */
public final class BackgroundTasksExtension implements BeforeEachCallback, AfterEachCallback {
    public static final String LOCK = "app.l2nx.highfive.partition.BackgroundTasks";
    public static final Instant NOW = Instant.parse("2026-01-05T12:00:00Z");

    private static final Namespace NAMESPACE = Namespace.create(BackgroundTasksExtension.class);
    private static final String PREVIOUS = "previous";

    private static volatile QueueingScheduler current;

    public static List<Pending> pending() {
        return List.copyOf(bound().queue);
    }

    /** Runs the work queued so far once; periodic work stays queued for the next call. */
    public static void runPending() {
        bound().runPending();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        final QueueingScheduler scheduler = new QueueingScheduler();
        final Previous previous = new Previous(
                Optional.ofNullable(current),
                Optional.ofNullable(BackgroundTasks.swap(new BackgroundTasks.Binding(scheduler, scheduler))));
        current = scheduler;
        context.getStore(NAMESPACE).put(PREVIOUS, previous);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        final Previous previous = context.getStore(NAMESPACE).get(PREVIOUS, Previous.class);
        current = previous.scheduler().orElse(null);
        BackgroundTasks.swap(previous.binding().orElse(null));
    }

    private static QueueingScheduler bound() {
        final QueueingScheduler scheduler = current;
        if (scheduler == null) {
            throw new IllegalStateException("No background tasks are bound: the test lacks BackgroundTasksExtension");
        }
        return scheduler;
    }

    /** {@code start} null: at once; {@code delay} null: one-shot. */
    public static final class Pending implements ScheduledFuture<Object> {
        private final Runnable task;
        private final Instant start;
        private final Duration delay;
        private boolean cancelled;

        private Pending(Runnable task, Instant start, Duration delay) {
            this.task = task;
            this.start = start;
            this.delay = delay;
        }

        public Instant start() {
            return start;
        }

        public Duration delay() {
            return delay;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException("A test runs background work through runPending()");
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return get();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return start == null ? 0 : unit.convert(Duration.between(NOW, start));
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }

    private static final class QueueingScheduler implements TaskScheduler, TaskExecutor {
        private final List<Pending> queue = new ArrayList<>();

        @Override
        public Clock getClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Override
        public void execute(Runnable task) {
            queue.add(new Pending(task, null, null));
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            return enqueue(new Pending(task, startTime, null));
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            return enqueue(new Pending(task, startTime, delay));
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            return enqueue(new Pending(task, NOW, delay));
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            throw new UnsupportedOperationException("BackgroundTasks schedules no trigger");
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            throw new UnsupportedOperationException("BackgroundTasks never runs at a fixed rate: a run may overlap");
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            throw new UnsupportedOperationException("BackgroundTasks never runs at a fixed rate: a run may overlap");
        }

        private Pending enqueue(Pending pending) {
            queue.add(pending);
            return pending;
        }

        void runPending() {
            final List<Pending> due = List.copyOf(queue);
            queue.clear();
            for (Pending pending : due) {
                if (pending.isCancelled()) {
                    continue;
                }
                if (pending.delay() != null) {
                    queue.add(pending);
                }
                pending.task.run();
            }
        }
    }

    private record Previous(Optional<QueueingScheduler> scheduler, Optional<BackgroundTasks.Binding> binding) {}
}
