/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Run time keyed by class: {@code type} is the fully qualified name, so an anonymous class never yields a blank tag.
 * The other tag keys follow Spring's {@code tasks.scheduled.execution}.
 */
public final class RunTimers {
    public static final String TYPE_TAG = "type";
    public static final String OUTCOME_TAG = "outcome";
    public static final String EXCEPTION_TAG = "exception";
    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR = "ERROR";
    public static final String NO_EXCEPTION = "none";

    private final Clock clock;
    private final MeterProvider<Timer> runTime;
    private final ClassValue<Timer> successTimers;

    public RunTimers(MeterRegistry registry, String name, String description) {
        clock = registry.config().clock();
        runTime = Timer.builder(name).description(description).withRegistry(registry);
        successTimers = new ClassValue<>() {
            @Override
            protected Timer computeValue(Class<?> type) {
                return runTime.withTags(TYPE_TAG, type.getName(), OUTCOME_TAG, SUCCESS, EXCEPTION_TAG, NO_EXCEPTION);
            }
        };
    }

    public void run(Class<?> type, Runnable work) {
        final Timer success = successTimers.get(type);
        final long start = clock.monotonicTime();
        try {
            work.run();
        } catch (Throwable t) {
            // The duration is taken before the error timer is looked up, which may register it.
            final long duration = clock.monotonicTime() - start;
            runTime.withTags(TYPE_TAG, type.getName(), OUTCOME_TAG, ERROR, EXCEPTION_TAG, exceptionName(t))
                    .record(duration, TimeUnit.NANOSECONDS);
            throw t;
        }
        success.record(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
    }

    // As Spring's DefaultServerRequestObservationConvention: the binary name stands in for an anonymous class.
    public static String exceptionName(Throwable failure) {
        final String simpleName = failure.getClass().getSimpleName();
        return simpleName.isEmpty() ? failure.getClass().getName() : simpleName;
    }
}
