/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.threads.metrics;

import app.l2nx.network.metrics.RunTimers;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

/**
 * Tag keys are those of Spring's {@code tasks.scheduled.execution}, plus the pool.
 * TODO: remove together with the legacy {@code ThreadPool}.
 */
public final class LegacyTaskMetrics {
    static final String TASK = "l2nx.legacy.task";
    static final String TASK_ALL = "l2nx.legacy.task.all";
    static final String TASK_LAG = "l2nx.legacy.task.lag";
    static final String POOL_TAG = "pool";
    static final String NAMESPACE_TAG = "code.namespace";
    static final String OUTCOME_TAG = "outcome";
    static final String EXCEPTION_TAG = "exception";
    static final String SUCCESS = "SUCCESS";
    static final String ERROR = "ERROR";
    static final String NO_EXCEPTION = "none";

    // Tasks and start lags take microseconds; the default 1 ms floor would put them all in the first bucket.
    private static final Duration HISTOGRAM_MINIMUM = Duration.ofNanos(1_000);

    private final Map<ThreadPoolKind, PoolMeters> pools = new EnumMap<>(ThreadPoolKind.class);

    public LegacyTaskMetrics(MeterRegistry registry, Set<ThreadPoolKind> pools) {
        final Timer all = Timer.builder(TASK_ALL)
                .description("Run time of all legacy ThreadPool tasks")
                .publishPercentileHistogram()
                .minimumExpectedValue(HISTOGRAM_MINIMUM)
                .register(registry);
        for (ThreadPoolKind pool : pools) {
            this.pools.put(pool, new PoolMeters(registry, pool, all));
        }
    }

    public TaskTimer timer(ThreadPoolKind pool, Class<?> taskType) {
        return meters(pool).timers.get(taskType);
    }

    public Timer lag(ThreadPoolKind pool) {
        if (!pool.isScheduled()) {
            throw new IllegalArgumentException(pool + " runs no scheduled tasks");
        }
        return meters(pool).lag;
    }

    private PoolMeters meters(ThreadPoolKind pool) {
        final PoolMeters meters = pools.get(pool);
        if (meters == null) {
            throw new IllegalArgumentException(pool + " is not running");
        }
        return meters;
    }

    public static final class TaskTimer {
        @Getter
        private final String namespace;

        private final Timer success;
        private final MeterProvider<Timer> runTime;
        private final Timer all;
        private final Clock clock;

        TaskTimer(String namespace, MeterProvider<Timer> runTime, Timer all, Clock clock) {
            this.namespace = namespace;
            this.success =
                    runTime.withTags(NAMESPACE_TAG, namespace, OUTCOME_TAG, SUCCESS, EXCEPTION_TAG, NO_EXCEPTION);
            this.runTime = runTime;
            this.all = all;
            this.clock = clock;
        }

        public void run(Runnable task) {
            final long start = clock.monotonicTime();
            try {
                task.run();
            } catch (Throwable t) {
                // The duration is taken before the error timer is looked up, which may register it.
                final long duration = clock.monotonicTime() - start;
                record(
                        runTime.withTags(
                                NAMESPACE_TAG,
                                namespace,
                                OUTCOME_TAG,
                                ERROR,
                                EXCEPTION_TAG,
                                RunTimers.exceptionName(t)),
                        duration);
                throw t;
            }
            record(success, clock.monotonicTime() - start);
        }

        private void record(Timer timer, long duration) {
            timer.record(duration, TimeUnit.NANOSECONDS);
            all.record(duration, TimeUnit.NANOSECONDS);
        }
    }

    private static final class PoolMeters {
        private final Timer lag;
        private final ClassValue<TaskTimer> timers;

        PoolMeters(MeterRegistry registry, ThreadPoolKind pool, Timer all) {
            lag = pool.isScheduled()
                    ? Timer.builder(TASK_LAG)
                            .description("Start delay of scheduled legacy ThreadPool tasks against their planned time")
                            .tag(POOL_TAG, pool.getTagValue())
                            .publishPercentileHistogram()
                            .minimumExpectedValue(HISTOGRAM_MINIMUM)
                            .register(registry)
                    : null;
            final MeterProvider<Timer> runTime = Timer.builder(TASK)
                    .description("Run time of legacy ThreadPool tasks per declaring class")
                    .tag(POOL_TAG, pool.getTagValue())
                    .withRegistry(registry);
            final Clock clock = registry.config().clock();
            timers = new ClassValue<>() {
                @Override
                protected TaskTimer computeValue(Class<?> type) {
                    return new TaskTimer(TaskClassNames.declaringClassName(type), runTime, all, clock);
                }
            };
        }
    }
}
