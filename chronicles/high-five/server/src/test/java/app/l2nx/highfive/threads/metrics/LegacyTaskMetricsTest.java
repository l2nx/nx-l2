/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.threads.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LegacyTaskMetricsTest {
    private static final String SCHEDULED = ThreadPoolKind.SCHEDULED.getTagValue();
    private static final String INSTANT = ThreadPoolKind.INSTANT.getTagValue();
    private static final String DECLARING_CLASS = LegacyTaskMetricsTest.class.getCanonicalName();
    // Lambdas of a @Nested class are created by that member class, which keeps its own name.
    private static final String RUN_TIME_CLASS = RunTime.class.getCanonicalName();
    private static final String OUTCOME_CLASS = Outcome.class.getCanonicalName();

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final LegacyTaskMetrics metrics =
            new LegacyTaskMetrics(registry, EnumSet.of(ThreadPoolKind.SCHEDULED, ThreadPoolKind.INSTANT));

    @Nested
    class RunTime {
        @Test
        void run_shouldRecordRunTime_taggedWithPoolNamespaceAndSuccess() {
            run(ThreadPoolKind.INSTANT, () -> clock.add(Duration.ofMillis(7)));

            final Timer actual = successTimer(INSTANT, RUN_TIME_CLASS);
            assertThat(actual.count()).isEqualTo(1);
            assertThat(actual.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(7.0);
        }

        @Test
        void timer_shouldShareOneTimer_whenLambdasComeFromOneClass() {
            run(ThreadPoolKind.INSTANT, () -> clock.add(Duration.ofMillis(2)));
            run(ThreadPoolKind.INSTANT, () -> clock.add(Duration.ofMillis(3)));

            assertThat(registry.find(LegacyTaskMetrics.TASK).timers()).hasSize(1);
            assertThat(successTimer(INSTANT, RUN_TIME_CLASS).count()).isEqualTo(2);
            assertThat(successTimer(INSTANT, RUN_TIME_CLASS).totalTime(TimeUnit.MILLISECONDS))
                    .isEqualTo(5.0);
        }

        @Test
        void run_shouldRecordIntoOneTimerOfAllTasks_acrossPools() {
            run(ThreadPoolKind.SCHEDULED, () -> clock.add(Duration.ofMillis(4)));
            run(ThreadPoolKind.INSTANT, new NamedTask(clock));

            final Timer all = registry.get(LegacyTaskMetrics.TASK_ALL).timer();
            assertThat(all.getId().getTags()).isEmpty();
            assertThat(all.count()).isEqualTo(2);
            assertThat(all.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(5.0);
            assertThat(successTimer(INSTANT, DECLARING_CLASS + ".NamedTask").count())
                    .isEqualTo(1);
        }

        @Test
        void getNamespace_shouldNameDeclaringClass() {
            final Runnable task = () -> {};

            assertThat(metrics.timer(ThreadPoolKind.INSTANT, task.getClass()).getNamespace())
                    .isEqualTo(RUN_TIME_CLASS);
        }
    }

    @Nested
    class Outcome {
        @Test
        void run_shouldRecordErrorAndRethrow_whenTaskThrows() {
            final IllegalStateException failure = new IllegalStateException("boom");
            final Runnable task = () -> {
                clock.add(Duration.ofMillis(5));
                throw failure;
            };

            assertThatThrownBy(() -> run(ThreadPoolKind.INSTANT, task)).isSameAs(failure);

            final Timer actual = errorTimer(OUTCOME_CLASS, "IllegalStateException");
            assertThat(actual.count()).isEqualTo(1);
            assertThat(actual.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(5.0);
            assertThat(successTimer(INSTANT, OUTCOME_CLASS).count()).isZero();
            assertThat(registry.get(LegacyTaskMetrics.TASK_ALL).timer().count()).isEqualTo(1);
        }

        @Test
        void run_shouldTagBinaryName_whenExceptionClassIsAnonymous() {
            final IllegalArgumentException failure = new IllegalArgumentException("boom") {};

            assertThatThrownBy(() -> run(ThreadPoolKind.INSTANT, () -> {
                        throw failure;
                    }))
                    .isSameAs(failure);

            assertThat(errorTimer(OUTCOME_CLASS, failure.getClass().getName()).count())
                    .isEqualTo(1);
        }

        @Test
        void run_shouldExcludeMeterRegistration_fromRunTimeOfFailedTask() {
            final SimpleMeterRegistry slowRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock) {
                @Override
                protected Timer newTimer(Meter.Id id, DistributionStatisticConfig config, PauseDetector pauseDetector) {
                    LegacyTaskMetricsTest.this.clock.add(Duration.ofMillis(100));
                    return super.newTimer(id, config, pauseDetector);
                }
            };
            final LegacyTaskMetrics slowMetrics =
                    new LegacyTaskMetrics(slowRegistry, EnumSet.of(ThreadPoolKind.INSTANT));
            final Runnable task = () -> {
                clock.add(Duration.ofMillis(5));
                throw new IllegalStateException("boom");
            };
            final LegacyTaskMetrics.TaskTimer timer = slowMetrics.timer(ThreadPoolKind.INSTANT, task.getClass());

            assertThatThrownBy(() -> timer.run(task)).isInstanceOf(IllegalStateException.class);

            assertThat(slowRegistry.get(LegacyTaskMetrics.TASK_ALL).timer().totalTime(TimeUnit.MILLISECONDS))
                    .isEqualTo(5.0);
        }
    }

    @Nested
    class Histogram {
        private final Map<String, DistributionStatisticConfig> configs = new ConcurrentHashMap<>();
        private final SimpleMeterRegistry capturing = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);

        Histogram() {
            capturing.config().meterFilter(new MeterFilter() {
                @Override
                public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                    configs.put(id.getName(), config.merge(DistributionStatisticConfig.DEFAULT));
                    return config;
                }
            });
            final LegacyTaskMetrics capturingMetrics =
                    new LegacyTaskMetrics(capturing, EnumSet.of(ThreadPoolKind.SCHEDULED));
            final Runnable task = () -> {};
            capturingMetrics.timer(ThreadPoolKind.SCHEDULED, task.getClass()).run(task);
        }

        // Legacy tasks and lags run in microseconds; the default 1 ms floor would put them all into the first bucket.
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {LegacyTaskMetrics.TASK_ALL, LegacyTaskMetrics.TASK_LAG})
        void constructor_shouldPublishHistogramFromOneMicrosecond(String name) {
            final DistributionStatisticConfig actual = configs.get(name);

            assertThat(actual.isPercentileHistogram()).isTrue();
            assertThat(actual.getMinimumExpectedValueAsDouble())
                    .isEqualTo(Duration.ofNanos(1_000).toNanos());
            assertThat(actual.getMaximumExpectedValueAsDouble())
                    .isEqualTo(Duration.ofSeconds(30).toNanos());
        }

        @Test
        void timer_shouldPublishNoHistogram_forRunTimePerClass() {
            assertThat(configs.get(LegacyTaskMetrics.TASK).isPercentileHistogram())
                    .isFalse();
        }
    }

    @Nested
    class Pools {
        @Test
        void constructor_shouldRegisterNoMeters_forPoolThatIsNotRunning() {
            assertThat(registry.find(LegacyTaskMetrics.TASK_LAG)
                            .tag(LegacyTaskMetrics.POOL_TAG, ThreadPoolKind.HIGH_PRIORITY_SCHEDULED.getTagValue())
                            .timer())
                    .isNull();
        }

        @Test
        void constructor_shouldRegisterNoLagTimer_forInstantPool() {
            assertThat(registry.find(LegacyTaskMetrics.TASK_LAG)
                            .tag(LegacyTaskMetrics.POOL_TAG, INSTANT)
                            .timer())
                    .isNull();
        }

        @Test
        void lag_shouldReturnPoolLagTimer_whenPoolSchedules() {
            assertThat(metrics.lag(ThreadPoolKind.SCHEDULED))
                    .isSameAs(registry.get(LegacyTaskMetrics.TASK_LAG)
                            .tag(LegacyTaskMetrics.POOL_TAG, SCHEDULED)
                            .timer());
        }

        @Test
        void lag_shouldFail_whenPoolDoesNotSchedule() {
            assertThatThrownBy(() -> metrics.lag(ThreadPoolKind.INSTANT)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lag_shouldFail_whenPoolIsNotRunning() {
            assertThatThrownBy(() -> metrics.lag(ThreadPoolKind.HIGH_PRIORITY_SCHEDULED))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void timer_shouldFail_whenPoolIsNotRunning() {
            assertThatThrownBy(() -> metrics.timer(ThreadPoolKind.HIGH_PRIORITY_SCHEDULED, Runnable.class))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private void run(ThreadPoolKind pool, Runnable task) {
        metrics.timer(pool, task.getClass()).run(task);
    }

    private Timer successTimer(String pool, String namespace) {
        return registry.get(LegacyTaskMetrics.TASK)
                .tags(
                        LegacyTaskMetrics.POOL_TAG,
                        pool,
                        LegacyTaskMetrics.NAMESPACE_TAG,
                        namespace,
                        LegacyTaskMetrics.OUTCOME_TAG,
                        LegacyTaskMetrics.SUCCESS,
                        LegacyTaskMetrics.EXCEPTION_TAG,
                        LegacyTaskMetrics.NO_EXCEPTION)
                .timer();
    }

    private Timer errorTimer(String namespace, String exception) {
        return registry.get(LegacyTaskMetrics.TASK)
                .tags(
                        LegacyTaskMetrics.POOL_TAG,
                        INSTANT,
                        LegacyTaskMetrics.NAMESPACE_TAG,
                        namespace,
                        LegacyTaskMetrics.OUTCOME_TAG,
                        LegacyTaskMetrics.ERROR,
                        LegacyTaskMetrics.EXCEPTION_TAG,
                        exception)
                .timer();
    }

    private static final class NamedTask implements Runnable {
        private final MockClock clock;

        NamedTask(MockClock clock) {
            this.clock = clock;
        }

        @Override
        public void run() {
            clock.add(Duration.ofMillis(1));
        }
    }
}
