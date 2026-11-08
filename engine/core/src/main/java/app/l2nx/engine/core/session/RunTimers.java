/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.Builder;

/** Run time keyed by class: {@code type} is the fully qualified name, so an anonymous class never yields a blank tag. */
final class RunTimers {
    public static final String TYPE_TAG = "type";
    public static final String OUTCOME_TAG = "outcome";
    public static final String EXCEPTION_TAG = "exception";
    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR = "ERROR";
    public static final String NO_EXCEPTION = "none";

    private final Clock clock;
    private final MeterProvider<Timer> runTime;
    private final MeterProvider<Timer> inputTime;
    private final ClassValue<SuccessTimers> successTimers;

    public RunTimers(MeterRegistry registry, String name, String description, MeterProvider<Timer> inputTime) {
        clock = registry.config().clock();
        runTime = Timer.builder(name).description(description).withRegistry(registry);
        this.inputTime = inputTime;
        successTimers = new ClassValue<>() {
            @Override
            protected SuccessTimers computeValue(Class<?> type) {
                final String[] tags = tags(type, SUCCESS, NO_EXCEPTION);
                return SuccessTimers.builder()
                        .run(runTime.withTags(tags))
                        .input(RunTimers.this.inputTime.withTags(tags))
                        .build();
            }
        };
    }

    public void run(Class<?> type, long enqueuedAt, Runnable work) {
        final SuccessTimers success = successTimers.get(type);
        final long start = clock.monotonicTime();
        try {
            work.run();
        } catch (Throwable t) {
            // The end is read before the error timers are looked up, which may register them.
            final long end = clock.monotonicTime();
            final String[] tags = tags(type, ERROR, exceptionName(t));
            runTime.withTags(tags).record(end - start, TimeUnit.NANOSECONDS);
            inputTime.withTags(tags).record(end - enqueuedAt, TimeUnit.NANOSECONDS);
            throw t;
        }
        final long end = clock.monotonicTime();
        success.run().record(end - start, TimeUnit.NANOSECONDS);
        success.input().record(end - enqueuedAt, TimeUnit.NANOSECONDS);
    }

    public long now() {
        return clock.monotonicTime();
    }

    // An anonymous class has an empty simple name; the binary name stands in.
    public static String exceptionName(Throwable failure) {
        final String simpleName = failure.getClass().getSimpleName();
        return simpleName.isEmpty() ? failure.getClass().getName() : simpleName;
    }

    private static String[] tags(Class<?> type, String outcome, String exception) {
        return new String[] {TYPE_TAG, type.getName(), OUTCOME_TAG, outcome, EXCEPTION_TAG, exception};
    }

    @Builder(toBuilder = true)
    private record SuccessTimers(Timer run, Timer input) {}
}
