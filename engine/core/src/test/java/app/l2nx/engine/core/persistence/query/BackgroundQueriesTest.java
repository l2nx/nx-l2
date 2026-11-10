/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

class BackgroundQueriesTest {
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final List<String> delivered = new ArrayList<>();

    @BeforeEach
    void stopTheClock() {
        when(scheduler.getClock()).thenReturn(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Nested
    class Run {
        @Test
        void run_shouldDeliverTheResultOnTheResultLoop() {
            final List<Runnable> loop = new ArrayList<>();
            final BackgroundQueries queries = new BackgroundQueries(Runnable::run, scheduler);

            queries.run(() -> "rows", loop::add, delivered::add);

            assertThat(delivered).isEmpty();
            loop.forEach(Runnable::run);
            assertThat(delivered).containsExactly("rows");
        }

        @Test
        void run_shouldDeliverNothingAndNotRetry_whenTheQueryFails() {
            final BackgroundQueries queries = new BackgroundQueries(Runnable::run, scheduler);

            queries.<String>run(
                    () -> {
                        throw new IllegalStateException("database down");
                    },
                    Runnable::run,
                    delivered::add);

            assertThat(delivered).isEmpty();
            verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        }

        @Test
        void run_shouldDeliverTheFailureOnTheResultLoop_whenTheQueryFails() {
            final List<Runnable> loop = new ArrayList<>();
            final List<RuntimeException> failures = new ArrayList<>();
            final BackgroundQueries queries = new BackgroundQueries(Runnable::run, scheduler);
            final IllegalStateException failure = new IllegalStateException("database down");

            queries.<String>run(
                    () -> {
                        throw failure;
                    },
                    loop::add,
                    delivered::add,
                    failures::add);

            assertThat(failures).as("not before the loop runs it").isEmpty();
            loop.forEach(Runnable::run);
            assertThat(failures).containsExactly(failure);
            assertThat(delivered).isEmpty();
        }

        @Test
        void run_shouldDeliverTheRefusalOnTheResultLoop_whenTheExecutorRejectsTheQuery() {
            final List<RuntimeException> failures = new ArrayList<>();
            final BackgroundQueries queries = new BackgroundQueries(
                    task -> {
                        throw new RejectedExecutionException("full");
                    },
                    scheduler);

            queries.run(() -> "rows", Runnable::run, delivered::add, failures::add);

            assertThat(failures).singleElement().isInstanceOf(RejectedExecutionException.class);
            assertThat(delivered).isEmpty();
        }

        @Test
        void run_shouldDeliverTheResultAndNoFailure_whenTheQueryAnswers() {
            final List<RuntimeException> failures = new ArrayList<>();
            final BackgroundQueries queries = new BackgroundQueries(Runnable::run, scheduler);

            queries.run(() -> "rows", Runnable::run, delivered::add, failures::add);

            assertThat(delivered).containsExactly("rows");
            assertThat(failures).isEmpty();
        }
    }

    @Nested
    class RunAfter {
        @Test
        void runAfter_shouldRunTheQueryAfterThePause() {
            final BackgroundQueries queries = new BackgroundQueries(Runnable::run, scheduler);

            queries.runAfter(Duration.ofSeconds(5), () -> "rows", Runnable::run, delivered::add);
            assertThat(delivered).isEmpty();
            final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            final ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
            verify(scheduler).schedule(task.capture(), start.capture());
            task.getValue().run();

            assertThat(start.getValue()).isEqualTo(Instant.EPOCH.plus(Duration.ofSeconds(5)));
            assertThat(delivered).containsExactly("rows");
        }
    }

    @Nested
    class RunUntilAnswered {
        @Test
        void runUntilAnswered_shouldReadAgainAfterEachPause_untilTheReadAnswers() {
            final BackgroundQueries queries = new BackgroundQueries(Runnable::run, scheduler);
            final AtomicInteger reads = new AtomicInteger();

            queries.runUntilAnswered(
                    () -> {
                        if (reads.incrementAndGet() < 5) {
                            throw new IllegalStateException("database down");
                        }
                        return "rows";
                    },
                    Runnable::run,
                    delivered::add);
            final List<Instant> starts = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                assertThat(delivered).isEmpty();
                final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
                final ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
                verify(scheduler, times(i + 1)).schedule(task.capture(), start.capture());
                starts.add(start.getValue());
                task.getValue().run();
            }

            assertThat(delivered).containsExactly("rows");
            assertThat(starts)
                    .containsExactly(
                            Instant.EPOCH.plus(Duration.ofSeconds(1)),
                            Instant.EPOCH.plus(Duration.ofSeconds(5)),
                            Instant.EPOCH.plus(Duration.ofSeconds(30)),
                            Instant.EPOCH.plus(Duration.ofSeconds(60)));
        }

        @Test
        void runUntilAnswered_shouldReadAgain_whenTheExecutorRejectedTheRead() {
            final AtomicInteger offered = new AtomicInteger();
            final BackgroundQueries queries = new BackgroundQueries(
                    task -> {
                        if (offered.incrementAndGet() == 1) {
                            throw new RejectedExecutionException("full");
                        }
                        task.run();
                    },
                    scheduler);

            queries.runUntilAnswered(() -> "rows", Runnable::run, delivered::add);
            final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).schedule(task.capture(), any(Instant.class));
            task.getValue().run();

            assertThat(delivered).containsExactly("rows");
        }
    }
}
