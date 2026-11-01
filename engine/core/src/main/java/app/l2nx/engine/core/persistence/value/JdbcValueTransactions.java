/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import app.l2nx.engine.core.identity.EntityIds;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * READ COMMITTED so a racing conditional update re-reads the row after the winner commits instead of failing to
 * serialize. The key is claimed before {@link ValueTransaction#execute}: a concurrent duplicate waits and gets
 * {@link ValueOutcome.Replayed}.
 */
@Slf4j
public final class JdbcValueTransactions implements ValueTransactions {
    public static final String TIMER = "l2nx.value.transaction";
    public static final String TYPE_TAG = "type";
    public static final String OUTCOME_TAG = "outcome";

    private final ValueTransactionRepository repository;
    private final TransactionTemplate transactions;
    private final Executor executor;
    private final Clock clock;
    private final MeterRegistry registry;
    private final MeterProvider<Timer> timers;

    public JdbcValueTransactions(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Executor executor,
            Clock clock,
            MeterRegistry registry) {
        this.repository = new ValueTransactionRepository(jdbcTemplate);
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.registry = registry;
        this.timers = Timer.builder(TIMER)
                .description("Run time of value transactions from the submit to the outcome")
                .withRegistry(registry);
    }

    @Override
    public <R> void submit(ValueTransaction<R> transaction, Executor resultLoop, Consumer<ValueOutcome<R>> onOutcome) {
        final Timer.Sample sample = Timer.start(registry);
        try {
            executor.execute(() -> complete(transaction, sample, run(transaction), resultLoop, onOutcome));
        } catch (RejectedExecutionException e) {
            log.error(
                    "Value transaction {} {} not started: the executor is full or stopped",
                    transaction.type(),
                    transaction.idempotencyKey(),
                    e);
            complete(transaction, sample, new ValueOutcome.Failed<>(e), resultLoop, onOutcome);
        }
    }

    // Claims the key instead of reading it: a read misses a transaction still committing on a dropped connection, while
    // the claim waits for it and, if it got through, is rolled back.
    @Override
    public void lookup(
            ValueTransactionType type,
            UUID idempotencyKey,
            Executor resultLoop,
            Consumer<ValueOutcome<Void>> onOutcome) {
        try {
            executor.execute(() -> {
                final ValueOutcome<Void> outcome = probe(type, idempotencyKey);
                offer(resultLoop, () -> onOutcome.accept(outcome), idempotencyKey);
            });
        } catch (RejectedExecutionException e) {
            log.error("Lookup of value transaction {} not started: the executor is full or stopped", idempotencyKey, e);
            offer(resultLoop, () -> onOutcome.accept(new ValueOutcome.Unknown<>(e)), idempotencyKey);
        }
    }

    private ValueOutcome<Void> probe(ValueTransactionType type, UUID idempotencyKey) {
        try {
            return transactions.execute(status -> {
                status.setRollbackOnly();
                repository.limitLockWait();
                if (repository.claim(EntityIds.next(), idempotencyKey, type, clock.instant())) {
                    return new ValueOutcome.Failed<Void>(
                            new IllegalStateException("Value transaction " + idempotencyKey + " was not committed"));
                }
                final ValueRecord recorded = repository
                        .find(idempotencyKey)
                        .orElseThrow(
                                () -> new IllegalStateException("Claimed key " + idempotencyKey + " has no record"));
                return ValueOutcome.Replayed.<Void>builder()
                        .transactionId(recorded.transactionId())
                        .subjectId(recorded.subjectId())
                        .movements(repository.findLedger(recorded.transactionId()))
                        .build();
            });
        } catch (RuntimeException e) {
            log.warn("Lookup of value transaction {} has no answer yet", idempotencyKey, e);
            return new ValueOutcome.Unknown<>(e);
        }
    }

    private <R> ValueOutcome<R> run(ValueTransaction<R> transaction) {
        final Attempt<R> attempt = new Attempt<>(transaction, EntityIds.next(), clock.instant());
        try {
            return transactions.execute(attempt::run);
        } catch (RuntimeException | Error e) {
            if (attempt.committing) {
                log.warn(
                        "Commit of value transaction {} {} has an unknown outcome",
                        transaction.type(),
                        transaction.idempotencyKey(),
                        e);
                return new ValueOutcome.Unknown<>(e);
            }
            log.error(
                    "Value transaction {} {} failed and was rolled back",
                    transaction.type(),
                    transaction.idempotencyKey(),
                    e);
            return new ValueOutcome.Failed<>(e);
        }
    }

    private <R> void complete(
            ValueTransaction<R> transaction,
            Timer.Sample sample,
            ValueOutcome<R> outcome,
            Executor resultLoop,
            Consumer<ValueOutcome<R>> onOutcome) {
        sample.stop(timers.withTags(
                TYPE_TAG, transaction.type().name().toLowerCase(Locale.ROOT), OUTCOME_TAG, outcomeTag(outcome)));
        offer(resultLoop, () -> onOutcome.accept(outcome), transaction.idempotencyKey());
    }

    private static void offer(Executor resultLoop, Runnable delivery, UUID idempotencyKey) {
        try {
            resultLoop.execute(delivery);
        } catch (RejectedExecutionException e) {
            log.warn("Result of value transaction {} not delivered: the loop has stopped", idempotencyKey, e);
        }
    }

    private static String outcomeTag(ValueOutcome<?> outcome) {
        return switch (outcome) {
            case ValueOutcome.Committed<?> committed -> "committed";
            case ValueOutcome.Replayed<?> replayed -> "replayed";
            case ValueOutcome.Refused<?> refused -> "refused";
            case ValueOutcome.Failed<?> failed -> "failed";
            case ValueOutcome.Unknown<?> unknown -> "unknown";
        };
    }

    /** One run of a transaction; {@link #committing} tells a failed commit from a failure before it. */
    private final class Attempt<R> implements ValueContext {
        private final ValueTransaction<R> transaction;
        private final UUID transactionId;
        private final Instant now;
        private final List<ItemMovement> movements = new ArrayList<>();
        private boolean committing;

        Attempt(ValueTransaction<R> transaction, UUID transactionId, Instant now) {
            this.transaction = transaction;
            this.transactionId = transactionId;
            this.now = now;
        }

        ValueOutcome<R> run(TransactionStatus status) {
            final UUID key = transaction.idempotencyKey();
            if (!repository.claim(transactionId, key, transaction.type(), now)) {
                status.setRollbackOnly();
                final ValueRecord recorded = repository
                        .find(key)
                        .orElseThrow(() -> new IllegalStateException("Claimed key " + key + " has no record"));
                return ValueOutcome.Replayed.<R>builder()
                        .transactionId(recorded.transactionId())
                        .subjectId(recorded.subjectId())
                        .movements(repository.findLedger(recorded.transactionId()))
                        .build();
            }
            final R result;
            try {
                result = transaction.execute(this);
            } catch (ValueRefusal refusal) {
                status.setRollbackOnly();
                return ValueOutcome.Refused.<R>builder()
                        .reason(refusal.reason())
                        .param(refusal.param())
                        .build();
            }
            final UUID subjectId = transaction.subjectId(result);
            if (subjectId != null) {
                repository.recordSubject(transactionId, subjectId);
            }
            repository.insertLedger(transactionId, movements);
            committing = true;
            return ValueOutcome.Committed.<R>builder()
                    .result(result)
                    .transactionId(transactionId)
                    .build();
        }

        @Override
        public JdbcClient jdbc() {
            return repository.jdbcClient();
        }

        @Override
        public UUID transactionId() {
            return transactionId;
        }

        @Override
        public Instant now() {
            return now;
        }

        @Override
        public void record(ItemMovement movement) {
            movements.add(Objects.requireNonNull(movement, "movement"));
        }
    }
}
