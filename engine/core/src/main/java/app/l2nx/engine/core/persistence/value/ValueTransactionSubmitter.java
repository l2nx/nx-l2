/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;

/**
 * An unknown outcome is looked up by its key after the pauses of {@link RetryBackoff} until the database answers, so
 * every submission ends in one call of its {@link ValueOutcomeHandler}.
 */
@Slf4j
@RequiredArgsConstructor
public class ValueTransactionSubmitter {
    private final ValueTransactions transactions;
    private final TaskScheduler scheduler;

    /** Every outcome, the lookups' included, is handled on {@code loop}. */
    public <R> void submit(ValueTransaction<R> transaction, Executor loop, ValueOutcomeHandler<R> handler) {
        transactions.submit(transaction, loop, outcome -> {
            switch (outcome) {
                case ValueOutcome.Committed<R> committed -> handler.committed(committed.result());
                case ValueOutcome.Replayed<R> replayed -> handler.replayed(replayed.movements());
                case ValueOutcome.Refused<R> refused -> handler.refused(refused.reason(), refused.param());
                case ValueOutcome.Failed<R> failed -> handler.failed();
                case ValueOutcome.Unknown<R> unknown -> lookup(transaction, loop, handler, 0);
            }
        });
    }

    private <R> void lookup(
            ValueTransaction<R> transaction, Executor loop, ValueOutcomeHandler<R> handler, int attempt) {
        transactions.lookup(transaction.type(), transaction.idempotencyKey(), loop, resolved -> {
            switch (resolved) {
                case ValueOutcome.Replayed<Void> replayed -> handler.replayed(replayed.movements());
                case ValueOutcome.Committed<Void> committed -> handler.replayed(List.of());
                case ValueOutcome.Failed<Void> failed -> handler.failed();
                case ValueOutcome.Refused<Void> refused -> handler.failed();
                case ValueOutcome.Unknown<Void> still -> retry(transaction, loop, handler, attempt, still);
            }
        });
    }

    // The caller's reservations stay meanwhile: the rows may have moved, and a save of memory would write over them.
    private <R> void retry(
            ValueTransaction<R> transaction,
            Executor loop,
            ValueOutcomeHandler<R> handler,
            int attempt,
            ValueOutcome.Unknown<Void> unknown) {
        final Duration pause = RetryBackoff.pause(attempt);
        log.warn(
                "Outcome of {} {} still unknown, looking it up again in {}",
                transaction.type(),
                transaction.idempotencyKey(),
                pause,
                unknown.cause());
        try {
            scheduler.schedule(
                    () -> lookup(transaction, loop, handler, attempt + 1),
                    scheduler.getClock().instant().plus(pause));
        } catch (TaskRejectedException e) {
            log.error(
                    "Outcome of {} {} is not looked up again: the scheduler has stopped",
                    transaction.type(),
                    transaction.idempotencyKey(),
                    e);
        }
    }
}
