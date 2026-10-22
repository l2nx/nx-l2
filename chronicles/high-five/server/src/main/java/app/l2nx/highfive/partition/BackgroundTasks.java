/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;

/**
 * Work with no game state on Spring Boot's auto-configured scheduler and executor; a result for the world goes back
 * through {@link WorldPartition#offer}.
 *
 * <p>TODO: goes once its callers become beans that receive the scheduler (E10, #41).
 */
public final class BackgroundTasks {
    private static volatile Binding binding;

    private BackgroundTasks() {}

    static void bind(TaskScheduler scheduler, TaskExecutor executor) {
        binding = new Binding(scheduler, executor);
    }

    static Binding swap(Binding next) {
        final Binding previous = binding;
        binding = next;
        return previous;
    }

    public static ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        final TaskScheduler scheduler = current().scheduler();
        return scheduler.schedule(task, startAt(scheduler, delay, unit));
    }

    public static ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable task, long initialDelay, long delay, TimeUnit unit) {
        final TaskScheduler scheduler = current().scheduler();
        return scheduler.scheduleWithFixedDelay(
                task, startAt(scheduler, initialDelay, unit), Duration.of(delay, unit.toChronoUnit()));
    }

    public static void execute(Runnable task) {
        current().executor().execute(task);
    }

    // The scheduler's own clock, not the system one: it measures the delay against the same clock.
    private static Instant startAt(TaskScheduler scheduler, long delay, TimeUnit unit) {
        return scheduler.getClock().instant().plus(delay, unit.toChronoUnit());
    }

    private static Binding current() {
        final Binding current = binding;
        if (current == null) {
            throw new IllegalStateException("Background tasks are not bound: used outside the Spring context");
        }
        return current;
    }

    record Binding(TaskScheduler scheduler, TaskExecutor executor) {
        Binding {
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(executor, "executor");
        }
    }
}
