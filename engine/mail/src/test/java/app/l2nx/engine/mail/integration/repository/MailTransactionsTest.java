/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.ValueOutcome;
import app.l2nx.engine.core.persistence.value.ValueTransaction;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.IncomingTargets;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.repository.CancelTransaction;
import app.l2nx.engine.mail.repository.ExpireTransaction;
import app.l2nx.engine.mail.repository.ReceiveTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The transactions against each other on a real runner: the row of the mail serializes them, so of two racing
 * settlements exactly one commits.
 */
class MailTransactionsTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    private final ExecutorService runner = Executors.newFixedThreadPool(2);

    @AutoClose
    private final MailWorld world = new MailWorld(MailSettings.defaults(), runner);

    private final UUID sender = world.character("Sender");
    private final UUID receiver = world.character("Receiver");
    private final Queue<ValueOutcome<?>> outcomes = new ConcurrentLinkedQueue<>();

    @AfterEach
    void stopRunner() {
        runner.shutdownNow();
    }

    private UUID attachedMail(Long codAdena) {
        final UUID mailId = world.insert(world.mail(sender, receiver)
                .attachments(AttachmentState.ATTACHED)
                .codAdena(codAdena)
                .build());
        world.attach(mailId, SWORD, 1);
        world.attach(mailId, ARROW, 20);
        return mailId;
    }

    private ReceiveTransaction receive(UUID mailId, Long codAdena, IncomingTargets targets) {
        return ReceiveTransaction.builder()
                .writer(world.writer)
                .idempotencyKey(EntityIds.next())
                .mailId(mailId)
                .receiver(targets)
                .senderId(sender)
                .codAdena(codAdena)
                .charge(codAdena != null)
                .build();
    }

    private static IncomingTargets offline(UUID characterId) {
        return IncomingTargets.builder()
                .characterId(characterId)
                .stacks(List.of())
                .build();
    }

    private <R> void submit(ValueTransaction<R> transaction) {
        world.transactions.submit(transaction, Runnable::run, outcomes::add);
    }

    /** Runs both while the mail row is locked, so both wait on it and race when it is released. */
    private void race(UUID mailId, ValueTransaction<?> first, ValueTransaction<?> second) throws SQLException {
        try (Connection lock = world.dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (PreparedStatement select = lock.prepareStatement("SELECT 1 FROM mails WHERE id = ? FOR UPDATE")) {
                select.setObject(1, mailId);
                select.executeQuery().close();
            }
            submit(first);
            submit(second);
            await(() -> waitingOnLocks() == 2);
            lock.rollback();
        }
        await(() -> outcomes.size() == 2);
    }

    private int waitingOnLocks() {
        return world.jdbc.sql("""
            SELECT count(*) FROM pg_stat_activity
            WHERE usename = current_user AND wait_event_type = 'Lock'
            """).query(Integer.class).single();
    }

    private static void await(BooleanSupplier condition) {
        final long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for the transactions");
            }
            Thread.onSpinWait();
        }
    }

    private void assertOneWinner() {
        assertThat(outcomes).hasSize(2);
        assertThat(outcomes)
                .filteredOn(ValueOutcome.Committed.class::isInstance)
                .hasSize(1);
        assertThat(outcomes)
                .filteredOn(ValueOutcome.Refused.class::isInstance)
                .singleElement()
                .satisfies(refused -> assertThat(((ValueOutcome.Refused<?>) refused).reason())
                        .isEqualTo(MailRefusal.ALREADY_RECEIVED));
    }

    @Nested
    class Races {
        @Test
        void receive_shouldHaveOneWinner_whenACancelRacesIt() throws SQLException {
            final UUID mailId = attachedMail(null);

            race(
                    mailId,
                    receive(mailId, null, offline(receiver)),
                    CancelTransaction.builder()
                            .writer(world.writer)
                            .idempotencyKey(EntityIds.next())
                            .mailId(mailId)
                            .sender(offline(sender))
                            .build());

            assertOneWinner();
            final AttachmentState state = world.row(mailId).attachments();
            assertThat(state).isIn(AttachmentState.RECEIVED, AttachmentState.CANCELLED);
            final UUID winner = state == AttachmentState.RECEIVED ? receiver : sender;
            assertThat(world.count(Holder.inventory(winner), SWORD)).isOne();
            assertThat(world.count(Holder.inventory(winner), ARROW)).isEqualTo(20);
            assertThat(world.store.itemsInMail(mailId)).isEmpty();
        }

        @Test
        void receive_shouldHaveOneWinner_whenTheExpiryRacesIt() throws SQLException {
            final UUID mailId = attachedMail(null);
            world.clock.advanceWall(Duration.ofHours(360));

            race(
                    mailId,
                    receive(mailId, null, offline(receiver)),
                    ExpireTransaction.builder()
                            .writer(world.writer)
                            .idempotencyKey(EntityIds.next())
                            .mailId(mailId)
                            .ownerId(sender)
                            .build());

            assertOneWinner();
            final AttachmentState state = world.row(mailId).attachments();
            assertThat(state).isIn(AttachmentState.RECEIVED, AttachmentState.EXPIRED);
            final Holder winner =
                    state == AttachmentState.RECEIVED ? Holder.inventory(receiver) : Holder.warehouse(sender);
            assertThat(world.count(winner, SWORD)).isOne();
        }
    }
}
