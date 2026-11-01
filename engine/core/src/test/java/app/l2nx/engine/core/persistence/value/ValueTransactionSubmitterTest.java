/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

class ValueTransactionSubmitterTest {
    private static final ItemMovement MOVEMENT = ItemMovement.builder()
            .holderType(HolderType.INVENTORY)
            .holderId(EntityIds.next())
            .itemTemplateId(57)
            .itemId(EntityIds.next())
            .delta(-100)
            .build();

    private final QueuedTransactions transactions = new QueuedTransactions();
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ValueTransactionSubmitter submitter = new ValueTransactionSubmitter(transactions, scheduler);
    private final RecordingHandler handler = new RecordingHandler();

    private enum Reason {
        NO_ITEMS
    }

    @BeforeEach
    void stopTheClock() {
        when(scheduler.getClock()).thenReturn(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Nested
    class Submit {
        @Test
        void submit_shouldHandleTheResult_whenCommitted() {
            transactions.outcomes.add(new ValueOutcome.Committed<>("mail", EntityIds.next()));

            submitter.submit(new TestTransaction(), Runnable::run, handler);

            assertThat(handler.calls).containsExactly("committed mail");
        }

        @Test
        void submit_shouldHandleTheMovements_whenReplayed() {
            transactions.outcomes.add(new ValueOutcome.Replayed<>(EntityIds.next(), null, List.of(MOVEMENT)));

            submitter.submit(new TestTransaction(), Runnable::run, handler);

            assertThat(handler.calls).containsExactly("replayed " + List.of(MOVEMENT));
        }

        @Test
        void submit_shouldPassTheConsumersReason_whenRefused() {
            transactions.outcomes.add(new ValueOutcome.Refused<>(Reason.NO_ITEMS, "Sword"));

            submitter.submit(new TestTransaction(), Runnable::run, handler);

            assertThat(handler.calls).containsExactly("refused NO_ITEMS Sword");
        }

        @Test
        void submit_shouldFail_whenTheTransactionFailed() {
            transactions.outcomes.add(new ValueOutcome.Failed<>(new IllegalStateException("lost")));

            submitter.submit(new TestTransaction(), Runnable::run, handler);

            assertThat(handler.calls).containsExactly("failed");
        }
    }

    @Nested
    class UnknownOutcome {
        @Test
        void submit_shouldHandleTheLookedUpMovements_whenTheLookupFindsTheKeyCommitted() {
            transactions.outcomes.add(unknown());
            transactions.lookups.add(new ValueOutcome.Replayed<>(EntityIds.next(), null, List.of(MOVEMENT)));

            submitter.submit(new TestTransaction(), Runnable::run, handler);

            assertThat(handler.calls).containsExactly("replayed " + List.of(MOVEMENT));
            verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        }

        @Test
        void submit_shouldLookUpAgainAfterAPause_untilTheDatabaseAnswers() {
            transactions.outcomes.add(unknown());
            transactions.lookups.add(unknown());
            transactions.lookups.add(new ValueOutcome.Failed<>(new IllegalStateException("not committed")));

            submitter.submit(new TestTransaction(), Runnable::run, handler);
            assertThat(handler.calls).isEmpty();
            final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            final ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
            verify(scheduler).schedule(task.capture(), start.capture());
            task.getValue().run();

            assertThat(start.getValue()).isEqualTo(Instant.EPOCH.plus(RetryBackoff.pause(0)));
            assertThat(handler.calls).containsExactly("failed");
        }
    }

    private static <R> ValueOutcome<R> unknown() {
        return new ValueOutcome.Unknown<>(new IllegalStateException("connection lost"));
    }

    private static final class TestTransaction implements ValueTransaction<String> {
        private final UUID idempotencyKey = EntityIds.next();

        @Override
        public ValueTransactionType type() {
            return ValueTransactionType.MAIL_SEND;
        }

        @Override
        public UUID idempotencyKey() {
            return idempotencyKey;
        }

        @Override
        public String execute(ValueContext context) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class QueuedTransactions implements ValueTransactions {
        private final Queue<ValueOutcome<?>> outcomes = new ArrayDeque<>();
        private final Queue<ValueOutcome<Void>> lookups = new ArrayDeque<>();

        @Override
        @SuppressWarnings("unchecked")
        public <R> void submit(
                ValueTransaction<R> transaction, Executor resultLoop, Consumer<ValueOutcome<R>> onOutcome) {
            final ValueOutcome<R> outcome = (ValueOutcome<R>) outcomes.remove();
            resultLoop.execute(() -> onOutcome.accept(outcome));
        }

        @Override
        public void lookup(
                ValueTransactionType type,
                UUID idempotencyKey,
                Executor resultLoop,
                Consumer<ValueOutcome<Void>> onOutcome) {
            final ValueOutcome<Void> outcome = lookups.remove();
            resultLoop.execute(() -> onOutcome.accept(outcome));
        }
    }

    private static final class RecordingHandler implements ValueOutcomeHandler<String> {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void committed(String result) {
            calls.add("committed " + result);
        }

        @Override
        public void replayed(List<ItemMovement> movements) {
            calls.add("replayed " + movements);
        }

        @Override
        public void refused(Enum<?> reason, String param) {
            calls.add("refused " + reason + " " + param);
        }

        @Override
        public void failed() {
            calls.add("failed");
        }
    }
}
