/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.EntityIds;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/** {@code value_transactions} and {@code value_ledger}; every write runs inside the caller's transaction. */
final class ValueTransactionRepository {
    // Waits for an uncommitted row with the same key and then inserts nothing, so a concurrent duplicate never runs.
    private static final String CLAIM = """
        INSERT INTO value_transactions (id, idempotency_key, type, committed_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (idempotency_key) DO NOTHING
        """;
    private static final String RECORD_SUBJECT = """
        UPDATE value_transactions SET subject_id = ? WHERE id = ?
        """;
    private static final String INSERT_LEDGER = """
        INSERT INTO value_ledger (id, transaction_id, holder_type, holder_id, item_template_id, item_id, delta)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
    // A lookup must not hang on a transaction whose session is gone but not yet ended by the server.
    private static final String LIMIT_LOCK_WAIT = "SET LOCAL lock_timeout = '5s'";
    private static final String FIND = """
        SELECT id, idempotency_key, type, subject_id, committed_at
        FROM value_transactions
        WHERE idempotency_key = ?
        """;

    private static final String FIND_LEDGER = """
        SELECT holder_type, holder_id, item_template_id, item_id, delta
        FROM value_ledger
        WHERE transaction_id = ?
        ORDER BY id
        """;

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbcClient;

    ValueTransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcClient = JdbcClient.create(jdbcTemplate);
    }

    JdbcClient jdbcClient() {
        return jdbcClient;
    }

    /** {@code false} when the key is already committed: nothing was written. */
    boolean claim(UUID transactionId, UUID idempotencyKey, ValueTransactionType type, Instant now) {
        return jdbcClient
                        .sql(CLAIM)
                        .param(transactionId)
                        .param(idempotencyKey)
                        .param(type.name())
                        .param(JdbcTimes.utc(now))
                        .update()
                == 1;
    }

    void limitLockWait() {
        jdbcClient.sql(LIMIT_LOCK_WAIT).update();
    }

    void recordSubject(UUID transactionId, UUID subjectId) {
        jdbcClient.sql(RECORD_SUBJECT).param(subjectId).param(transactionId).update();
    }

    void insertLedger(UUID transactionId, List<ItemMovement> movements) {
        if (movements.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_LEDGER, movements, movements.size(), (statement, movement) -> {
            statement.setObject(1, EntityIds.next());
            statement.setObject(2, transactionId);
            statement.setString(3, movement.holderType().name());
            statement.setObject(4, movement.holderId());
            statement.setInt(5, movement.itemTemplateId());
            statement.setObject(6, movement.itemId());
            statement.setLong(7, movement.delta());
        });
    }

    List<ItemMovement> findLedger(UUID transactionId) {
        return jdbcClient
                .sql(FIND_LEDGER)
                .param(transactionId)
                .query((rs, rowNum) -> new ItemMovement(
                        HolderType.valueOf(rs.getString("holder_type")),
                        rs.getObject("holder_id", UUID.class),
                        rs.getInt("item_template_id"),
                        rs.getObject("item_id", UUID.class),
                        rs.getLong("delta")))
                .list();
    }

    Optional<ValueRecord> find(UUID idempotencyKey) {
        return jdbcClient
                .sql(FIND)
                .param(idempotencyKey)
                .query((rs, rowNum) -> ValueRecord.builder()
                        .transactionId(rs.getObject("id", UUID.class))
                        .idempotencyKey(rs.getObject("idempotency_key", UUID.class))
                        .type(ValueTransactionType.valueOf(rs.getString("type")))
                        .subjectId(rs.getObject("subject_id", UUID.class))
                        .committedAt(JdbcTimes.instant(rs, "committed_at"))
                        .build())
                .optional();
    }
}
