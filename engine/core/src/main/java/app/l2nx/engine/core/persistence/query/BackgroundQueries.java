/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.query;

import app.l2nx.engine.core.persistence.value.RetryBackoff;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;

/**
 * Runs database work that moves no value (reads, read marks, deletions) on Boot's task executor and hands the result to
 * a loop, so no JDBC runs on a loop thread.
 */
@Slf4j
@RequiredArgsConstructor
public class BackgroundQueries {
    private final Executor executor;
    private final TaskScheduler scheduler;

    /** A failed query is logged and delivers nothing: the caller reserved nothing that would wait for it. */
    public <T> void run(Supplier<T> query, Executor resultLoop, Consumer<T> onResult) {
        try {
            executor.execute(() -> {
                final T result;
                try {
                    result = query.get();
                } catch (RuntimeException e) {
                    log.error("Background query failed", e);
                    return;
                }
                deliver(resultLoop, onResult, result);
            });
        } catch (RejectedExecutionException e) {
            log.error("Background query not started: the executor is full or stopped", e);
        }
    }

    /**
     * Reads what memory must follow after a commit. The commit is real and the caller's reservations stay until memory
     * follows it, so a failed read is tried again after the pauses of {@link RetryBackoff} until it answers.
     */
    public <T> void runUntilAnswered(Supplier<T> query, Executor resultLoop, Consumer<T> onResult) {
        attempt(query, resultLoop, onResult, 0);
    }

    private <T> void attempt(Supplier<T> query, Executor resultLoop, Consumer<T> onResult, int attempt) {
        try {
            executor.execute(() -> {
                final T result;
                try {
                    result = query.get();
                } catch (RuntimeException e) {
                    retry(query, resultLoop, onResult, attempt, e);
                    return;
                }
                deliver(resultLoop, onResult, result);
            });
        } catch (RejectedExecutionException e) {
            retry(query, resultLoop, onResult, attempt, e);
        }
    }

    private <T> void retry(
            Supplier<T> query, Executor resultLoop, Consumer<T> onResult, int attempt, RuntimeException cause) {
        final Duration pause = RetryBackoff.pause(attempt);
        log.warn("Rows a commit needs could not be read, reading again in {}", pause, cause);
        try {
            scheduler.schedule(
                    () -> attempt(query, resultLoop, onResult, attempt + 1),
                    scheduler.getClock().instant().plus(pause));
        } catch (TaskRejectedException e) {
            log.error("Rows a commit needs are not read again: the scheduler has stopped", e);
        }
    }

    private static <T> void deliver(Executor loop, Consumer<T> onResult, T result) {
        try {
            loop.execute(() -> onResult.accept(result));
        } catch (RejectedExecutionException e) {
            log.warn("Background query result not delivered: the loop has stopped", e);
        }
    }
}
