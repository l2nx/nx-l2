/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.database.JdbcTimes;
import app.l2nx.database.PostgresDatabase;
import app.l2nx.engine.core.identity.EntityIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;

class JdbcValueTransactionsTest {
    private static final DataSource DATA_SOURCE = migrated();
    private static final JdbcClient JDBC = JdbcClient.create(DATA_SOURCE);
    private static final Instant NOW = Instant.parse("2027-01-15T08:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final JdbcValueTransactions transactions = transactions(new DataSourceTransactionManager(DATA_SOURCE));

    private enum Reason {
        NO_ITEMS
    }

    @FunctionalInterface
    private interface Body {
        UUID execute(ValueContext context) throws ValueRefusal;
    }

    private static final class TestTransaction implements ValueTransaction<UUID> {
        private final UUID idempotencyKey;
        private final Body body;

        TestTransaction(UUID idempotencyKey, Body body) {
            this.idempotencyKey = idempotencyKey;
            this.body = body;
        }

        @Override
        public ValueTransactionType type() {
            return ValueTransactionType.MAIL_SEND;
        }

        @Override
        public UUID idempotencyKey() {
            return idempotencyKey;
        }

        @Override
        public UUID execute(ValueContext context) throws ValueRefusal {
            return body.execute(context);
        }

        @Override
        public UUID subjectId(UUID result) {
            return result;
        }
    }

    private static DataSource migrated() {
        final DataSource dataSource = PostgresDatabase.migratedSchema("core", "db/engine/core/changelog.sql");
        // Stands in for a consumer's table, so a rollback of execute's own writes can be observed.
        JdbcClient.create(dataSource)
                .sql("CREATE TABLE test_values (id uuid PRIMARY KEY, written_at timestamptz NOT NULL)")
                .update();
        return dataSource;
    }

    private JdbcValueTransactions transactions(PlatformTransactionManager transactionManager) {
        return new JdbcValueTransactions(
                new JdbcTemplate(DATA_SOURCE),
                transactionManager,
                executor,
                Clock.fixed(NOW, ZoneOffset.UTC),
                registry);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    private static List<ItemMovement> ledger(UUID transactionId) {
        return JDBC.sql("""
            SELECT holder_type, holder_id, item_template_id, item_id, delta
            FROM value_ledger WHERE transaction_id = ? ORDER BY id
            """)
                .param(transactionId)
                .query((rs, rowNum) -> ItemMovement.builder()
                        .holderType(HolderType.valueOf(rs.getString("holder_type")))
                        .holderId(rs.getObject("holder_id", UUID.class))
                        .itemTemplateId(rs.getInt("item_template_id"))
                        .itemId(rs.getObject("item_id", UUID.class))
                        .delta(rs.getLong("delta"))
                        .build())
                .list();
    }

    private static UUID writeAndMove(ValueContext context) {
        final UUID id = EntityIds.next();
        context.jdbc()
                .sql("INSERT INTO test_values (id, written_at) VALUES (?, ?)")
                .param(id)
                .param(JdbcTimes.utc(context.now()))
                .update();
        final UUID itemId = EntityIds.next();
        context.recordAll(List.of(movement(HolderType.INVENTORY, itemId, -1), movement(HolderType.MAIL, itemId, 1)));
        return id;
    }

    private static ItemMovement movement(HolderType holderType, UUID itemId, long delta) {
        return ItemMovement.builder()
                .holderType(holderType)
                .holderId(EntityIds.next())
                .itemTemplateId(57)
                .itemId(itemId)
                .delta(delta)
                .build();
    }

    private static <R> ValueOutcome<R> submit(ValueTransactions transactions, ValueTransaction<R> transaction) {
        final CompletableFuture<ValueOutcome<R>> outcome = new CompletableFuture<>();
        transactions.submit(transaction, Runnable::run, outcome::complete);
        return outcome.orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS).join();
    }

    private static ValueOutcome<Void> lookup(ValueTransactions transactions, UUID idempotencyKey) {
        return await(startLookup(transactions, idempotencyKey));
    }

    private static CompletableFuture<ValueOutcome<Void>> startLookup(
            ValueTransactions transactions, UUID idempotencyKey) {
        final CompletableFuture<ValueOutcome<Void>> result = new CompletableFuture<>();
        transactions.lookup(ValueTransactionType.MAIL_SEND, idempotencyKey, Runnable::run, result::complete);
        return result;
    }

    private static <T> T await(CompletableFuture<T> future) {
        return future.orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS).join();
    }

    // A claim waits on another transaction's uncommitted key; only then is the race real.
    private static void awaitLockWait() throws InterruptedException {
        final long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (JDBC.sql("""
            SELECT count(*) FROM pg_stat_activity
            WHERE usename = current_user AND wait_event_type = 'Lock'
            """).query(Integer.class).single() == 0) {
            assertThat(System.nanoTime()).as("the claim waits").isLessThan(deadline);
            Thread.sleep(10);
        }
    }

    private static int testValues(UUID id) {
        return JDBC.sql("SELECT count(*) FROM test_values WHERE id = ?")
                .param(id)
                .query(Integer.class)
                .single();
    }

    private static int records(UUID idempotencyKey) {
        return JDBC.sql("SELECT count(*) FROM value_transactions WHERE idempotency_key = ?")
                .param(idempotencyKey)
                .query(Integer.class)
                .single();
    }

    @SuppressWarnings("unchecked")
    private static ValueOutcome.Committed<UUID> committed(ValueOutcome<UUID> outcome) {
        assertThat(outcome).isInstanceOf(ValueOutcome.Committed.class);
        return (ValueOutcome.Committed<UUID>) outcome;
    }

    @Nested
    class Submit {
        @Test
        void submit_shouldRecordTheTransactionAndItsLedger_whenExecuteSucceeds() {
            final UUID key = EntityIds.next();

            final ValueOutcome.Committed<UUID> actual =
                    committed(submit(transactions, new TestTransaction(key, JdbcValueTransactionsTest::writeAndMove)));

            assertThat(testValues(actual.result())).isOne();
            final Map<String, Object> recorded =
                    JDBC.sql("""
                        SELECT id, type, subject_id FROM value_transactions WHERE idempotency_key = ?
                        """).param(key).query().singleRow();
            assertThat(recorded)
                    .containsExactlyInAnyOrderEntriesOf(
                            Map.of("id", actual.transactionId(), "type", "MAIL_SEND", "subject_id", actual.result()));
            final List<Map<String, Object>> ledger = JDBC.sql(
                            "SELECT holder_type, delta FROM value_ledger WHERE transaction_id = ? ORDER BY delta")
                    .param(actual.transactionId())
                    .query()
                    .listOfRows();
            assertThat(ledger)
                    .containsExactly(
                            Map.of("holder_type", "INVENTORY", "delta", -1L),
                            Map.of("holder_type", "MAIL", "delta", 1L));
        }

        @Test
        void submit_shouldReplayTheRecordedSubject_whenTheKeyWasCommitted() {
            final UUID key = EntityIds.next();
            final AtomicInteger runs = new AtomicInteger();
            final TestTransaction transaction = new TestTransaction(key, context -> {
                runs.incrementAndGet();
                return writeAndMove(context);
            });
            final ValueOutcome.Committed<UUID> first = committed(submit(transactions, transaction));

            final ValueOutcome<UUID> actual = submit(transactions, transaction);

            assertThat(actual)
                    .isEqualTo(ValueOutcome.Replayed.<UUID>builder()
                            .transactionId(first.transactionId())
                            .subjectId(first.result())
                            .movements(ledger(first.transactionId()))
                            .build());
            assertThat(((ValueOutcome.Replayed<UUID>) actual).movements())
                    .extracting(ItemMovement::holderType, ItemMovement::delta)
                    .containsExactly(tuple(HolderType.INVENTORY, -1L), tuple(HolderType.MAIL, 1L));
            assertThat(runs).hasValue(1);
        }

        @Test
        void submit_shouldRollBackTheWrites_whenExecuteRefuses() {
            final UUID key = EntityIds.next();
            final UUID[] written = new UUID[1];

            final ValueOutcome<UUID> actual = submit(transactions, new TestTransaction(key, context -> {
                written[0] = writeAndMove(context);
                throw new ValueRefusal(Reason.NO_ITEMS, "57");
            }));

            assertThat(actual)
                    .isEqualTo(ValueOutcome.Refused.<UUID>builder()
                            .reason(Reason.NO_ITEMS)
                            .param("57")
                            .build());
            assertThat(testValues(written[0])).isZero();
            assertThat(records(key)).isZero();
        }

        @Test
        void submit_shouldFailWithNothingWritten_whenExecuteThrows() {
            final UUID key = EntityIds.next();
            final UUID[] written = new UUID[1];
            final IllegalStateException failure = new IllegalStateException("execute failed");

            final ValueOutcome<UUID> actual = submit(transactions, new TestTransaction(key, context -> {
                written[0] = writeAndMove(context);
                throw failure;
            }));

            assertThat(actual).isEqualTo(new ValueOutcome.Failed<UUID>(failure));
            assertThat(testValues(written[0])).isZero();
            assertThat(records(key)).isZero();
            assertThat(submit(transactions, new TestTransaction(key, JdbcValueTransactionsTest::writeAndMove)))
                    .as("a failed key stays free for a retry")
                    .isInstanceOf(ValueOutcome.Committed.class);
        }

        @Test
        void submit_shouldCommitOnce_whenTwoSubmitsShareAKey() throws InterruptedException {
            final UUID key = EntityIds.next();
            final AtomicInteger runs = new AtomicInteger();
            final CountDownLatch firstRunning = new CountDownLatch(1);
            final CountDownLatch release = new CountDownLatch(1);
            final TestTransaction transaction = new TestTransaction(key, context -> {
                runs.incrementAndGet();
                firstRunning.countDown();
                await(release);
                return writeAndMove(context);
            });
            final CompletableFuture<ValueOutcome<UUID>> first = new CompletableFuture<>();
            final CompletableFuture<ValueOutcome<UUID>> second = new CompletableFuture<>();
            transactions.submit(transaction, Runnable::run, first::complete);
            assertThat(firstRunning.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .isTrue();

            transactions.submit(transaction, Runnable::run, second::complete);
            awaitLockWait();
            release.countDown();

            final ValueOutcome.Committed<UUID> committed = committed(
                    first.orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS).join());
            assertThat(second.orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS).join())
                    .isEqualTo(ValueOutcome.Replayed.<UUID>builder()
                            .transactionId(committed.transactionId())
                            .subjectId(committed.result())
                            .movements(ledger(committed.transactionId()))
                            .build());
            assertThat(runs).hasValue(1);
        }

        @Test
        void submit_shouldReportUnknown_whenTheCommitFails() {
            final UUID key = EntityIds.next();
            final JdbcValueTransactions lostAfterCommit = transactions(new DataSourceTransactionManager(DATA_SOURCE) {
                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                    super.doCommit(status);
                    throw new TransactionSystemException("Connection lost after the commit");
                }
            });

            final ValueOutcome<UUID> actual =
                    submit(lostAfterCommit, new TestTransaction(key, JdbcValueTransactionsTest::writeAndMove));

            assertThat(actual).isInstanceOf(ValueOutcome.Unknown.class);
            assertThat(lookup(transactions, key)).isInstanceOf(ValueOutcome.Replayed.class);
        }

        @Test
        void submit_shouldChangeNothing_whenTheConnectionIsKilledBeforeTheCommit() {
            final UUID key = EntityIds.next();
            final UUID[] written = new UUID[1];
            final CompletableFuture<Integer> backend = new CompletableFuture<>();
            final CountDownLatch killed = new CountDownLatch(1);
            final CompletableFuture<ValueOutcome<UUID>> outcome = new CompletableFuture<>();
            transactions.submit(
                    new TestTransaction(key, context -> {
                        written[0] = writeAndMove(context);
                        backend.complete(context.jdbc()
                                .sql("SELECT pg_backend_pid()")
                                .query(Integer.class)
                                .single());
                        await(killed);
                        return written[0];
                    }),
                    Runnable::run,
                    outcome::complete);

            final boolean terminated = JDBC.sql("SELECT pg_terminate_backend(?, ?)")
                    .param(JdbcValueTransactionsTest.await(backend))
                    .param(TIMEOUT.toMillis())
                    .query(Boolean.class)
                    .single();
            killed.countDown();

            assertThat(terminated).isTrue();
            assertThat(JdbcValueTransactionsTest.await(outcome))
                    .isInstanceOfAny(ValueOutcome.Failed.class, ValueOutcome.Unknown.class);
            assertThat(testValues(written[0])).isZero();
            assertThat(records(key)).isZero();
            assertThat(lookup(transactions, key)).isInstanceOf(ValueOutcome.Failed.class);
        }

        @Test
        void submit_shouldFail_whenTheExecutorRejects() {
            final RejectedExecutionException rejection = new RejectedExecutionException("stopped");
            final JdbcValueTransactions rejecting = new JdbcValueTransactions(
                    new JdbcTemplate(DATA_SOURCE),
                    new DataSourceTransactionManager(DATA_SOURCE),
                    task -> {
                        throw rejection;
                    },
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    registry);

            final ValueOutcome<UUID> actual =
                    submit(rejecting, new TestTransaction(EntityIds.next(), JdbcValueTransactionsTest::writeAndMove));

            assertThat(actual).isEqualTo(new ValueOutcome.Failed<UUID>(rejection));
        }

        @Test
        void submit_shouldTimeTheOutcomeByTypeAndOutcome() {
            submit(transactions, new TestTransaction(EntityIds.next(), JdbcValueTransactionsTest::writeAndMove));

            assertThat(registry.get(JdbcValueTransactions.TIMER)
                            .tag(JdbcValueTransactions.TYPE_TAG, "mail_send")
                            .tag(JdbcValueTransactions.OUTCOME_TAG, "committed")
                            .timer()
                            .count())
                    .isOne();
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    @Nested
    class Lookup {
        @Test
        void lookup_shouldReplay_whenTheKeyWasCommitted() {
            final UUID key = EntityIds.next();
            final ValueOutcome.Committed<UUID> committed =
                    committed(submit(transactions, new TestTransaction(key, JdbcValueTransactionsTest::writeAndMove)));

            final ValueOutcome<Void> actual = lookup(transactions, key);

            assertThat(actual)
                    .isEqualTo(ValueOutcome.Replayed.<Void>builder()
                            .transactionId(committed.transactionId())
                            .subjectId(committed.result())
                            .movements(ledger(committed.transactionId()))
                            .build());
            assertThat(((ValueOutcome.Replayed<Void>) actual).movements()).hasSize(2);
        }

        @Test
        void lookup_shouldProbeOnTheExecutor_andAnswerOnTheResultLoop() {
            final List<String> probedOn = new CopyOnWriteArrayList<>();
            final JdbcValueTransactions recording = transactions(new DataSourceTransactionManager(DATA_SOURCE) {
                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                    probedOn.add(Thread.currentThread().getName());
                    super.doBegin(transaction, definition);
                }
            });
            final ExecutorService resultLoop =
                    Executors.newSingleThreadExecutor(task -> new Thread(task, "result-loop"));
            try {
                final CompletableFuture<String> answeredOn = new CompletableFuture<>();
                recording.lookup(
                        ValueTransactionType.MAIL_SEND,
                        EntityIds.next(),
                        resultLoop,
                        outcome -> answeredOn.complete(Thread.currentThread().getName()));

                assertThat(await(answeredOn)).isEqualTo("result-loop");
                assertThat(probedOn).isNotEmpty().doesNotContain("result-loop");
            } finally {
                resultLoop.shutdownNow();
            }
        }

        @Test
        void lookup_shouldFailAndLeaveTheKeyFree_whenTheKeyWasNotCommitted() {
            final UUID key = EntityIds.next();

            final ValueOutcome<Void> actual = lookup(transactions, key);

            assertThat(actual).isInstanceOf(ValueOutcome.Failed.class);
            assertThat(records(key)).isZero();
        }

        @Test
        void lookup_shouldReplayAfterTheCommit_whenAnotherTransactionHoldsTheKey() throws Exception {
            final UUID key = EntityIds.next();
            final UUID transactionId = EntityIds.next();
            final UUID subjectId = EntityIds.next();
            try (Connection holder = DATA_SOURCE.getConnection()) {
                holder.setAutoCommit(false);
                holdKey(holder, transactionId, key, subjectId);
                final CompletableFuture<ValueOutcome<Void>> lookup = startLookup(transactions, key);
                awaitLockWait();
                assertThat(lookup).isNotDone();

                holder.commit();

                assertThat(await(lookup))
                        .isEqualTo(ValueOutcome.Replayed.<Void>builder()
                                .transactionId(transactionId)
                                .subjectId(subjectId)
                                .movements(List.of())
                                .build());
            }
        }

        @Test
        void lookup_shouldFailAfterTheRollback_whenAnotherTransactionHoldsTheKey() throws Exception {
            final UUID key = EntityIds.next();
            try (Connection holder = DATA_SOURCE.getConnection()) {
                holder.setAutoCommit(false);
                holdKey(holder, EntityIds.next(), key, EntityIds.next());
                final CompletableFuture<ValueOutcome<Void>> lookup = startLookup(transactions, key);
                awaitLockWait();
                assertThat(lookup).isNotDone();

                holder.rollback();

                assertThat(await(lookup)).isInstanceOf(ValueOutcome.Failed.class);
                assertThat(records(key)).isZero();
            }
        }

        private void holdKey(Connection holder, UUID transactionId, UUID key, UUID subjectId) throws SQLException {
            try (PreparedStatement insert = holder.prepareStatement("""
                INSERT INTO value_transactions (id, idempotency_key, type, subject_id, committed_at)
                VALUES (?, ?, 'MAIL_SEND', ?, ?)
                """)) {
                insert.setObject(1, transactionId);
                insert.setObject(2, key);
                insert.setObject(3, subjectId);
                insert.setObject(4, JdbcTimes.utc(NOW));
                insert.executeUpdate();
            }
        }
    }
}
