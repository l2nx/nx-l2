/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.threads;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.threads.metrics.LegacyTaskMetrics;
import app.l2nx.highfive.threads.metrics.ThreadPoolKind;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RunnableWrapperTest {
    private static final String TASK_ALL = "l2nx.legacy.task.all";

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final LegacyTaskMetrics metrics = new LegacyTaskMetrics(registry, EnumSet.of(ThreadPoolKind.INSTANT));

    @Nested
    class Run {
        @Test
        void run_shouldRecordRunTime() {
            new RunnableWrapper(metrics, ThreadPoolKind.INSTANT, () -> clock.add(Duration.ofMillis(7))).run();

            final Timer all = registry.get(TASK_ALL).timer();
            assertThat(all.count()).isEqualTo(1);
            assertThat(all.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(7.0);
        }

        @Test
        void run_shouldReportToUncaughtHandler_whenTaskThrows() throws InterruptedException {
            final IllegalStateException failure = new IllegalStateException("boom");
            final Runnable task = new RunnableWrapper(metrics, ThreadPoolKind.INSTANT, () -> {
                throw failure;
            });
            final AtomicReference<Throwable> reported = new AtomicReference<>();
            final Thread thread = new Thread(task);
            thread.setUncaughtExceptionHandler((ignored, error) -> reported.set(error));

            thread.start();
            thread.join();

            assertThat(reported.get()).isSameAs(failure);
            assertThat(registry.get(TASK_ALL).timer().count()).isEqualTo(1);
        }

        @Test
        void toString_shouldNameDeclaringClass() {
            final Runnable task = () -> {};

            assertThat(new RunnableWrapper(metrics, ThreadPoolKind.INSTANT, task).toString())
                    .isEqualTo(Run.class.getCanonicalName());
        }
    }
}
