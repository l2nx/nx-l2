/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(BackgroundTasksTest.LOCK)
class BackgroundTasksTest {
    static final String LOCK = "app.l2nx.highfive.partition.BackgroundTasks";
    private static final Instant NOW = Instant.parse("2026-01-05T12:00:00Z");
    private static final Runnable TASK = () -> {};

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final TaskExecutor executor = mock(TaskExecutor.class);

    private BackgroundTasks.Binding previous;

    @BeforeEach
    void unbind() {
        previous = BackgroundTasks.swap(null);
        when(scheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void restore() {
        BackgroundTasks.swap(previous);
    }

    @Nested
    class Unbound {
        static Stream<Arguments> entries() {
            return Stream.of(
                    entry("schedule", () -> BackgroundTasks.schedule(TASK, 1, TimeUnit.SECONDS)),
                    entry(
                            "scheduleWithFixedDelay",
                            () -> BackgroundTasks.scheduleWithFixedDelay(TASK, 1, 1, TimeUnit.SECONDS)),
                    entry("execute", () -> BackgroundTasks.execute(TASK)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("entries")
        void entry_shouldThrow_whenUnbound(String name, ThrowingCallable entry) {
            assertThatIllegalStateException().isThrownBy(entry).withMessageContaining("not bound");
        }

        private static Arguments entry(String name, ThrowingCallable entry) {
            return Arguments.of(name, entry);
        }
    }

    @Nested
    class Schedule {
        @Test
        void schedule_shouldStartAfterTheDelay_onTheSchedulersClock() {
            BackgroundTasks.bind(scheduler, executor);

            BackgroundTasks.schedule(TASK, 1_500, TimeUnit.MICROSECONDS);

            verify(scheduler).schedule(TASK, NOW.plusNanos(1_500_000));
        }
    }

    @Nested
    class ScheduleWithFixedDelay {
        @Test
        void scheduleWithFixedDelay_shouldStartAfterTheInitialDelay_onTheSchedulersClock() {
            BackgroundTasks.bind(scheduler, executor);

            BackgroundTasks.scheduleWithFixedDelay(TASK, 2, 30, TimeUnit.SECONDS);

            verify(scheduler).scheduleWithFixedDelay(TASK, NOW.plusSeconds(2), Duration.ofSeconds(30));
        }
    }

    @Nested
    class Execute {
        @Test
        void execute_shouldRunOnTheBoundExecutor() {
            BackgroundTasks.bind(scheduler, executor);

            BackgroundTasks.execute(TASK);

            verify(executor).execute(TASK);
        }
    }
}
