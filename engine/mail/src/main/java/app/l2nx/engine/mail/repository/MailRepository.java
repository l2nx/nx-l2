/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.repository;

import static app.l2nx.database.JdbcTimes.instant;
import static app.l2nx.database.JdbcTimes.utc;

import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code mails} table. A mail is visible in a box until that side hides it or the expiry job processes it; every
 * change of {@code attachments} is a conditional update from {@code ATTACHED}, so racing changes have one winner.
 */
@Repository
@RequiredArgsConstructor
public class MailRepository {
    private static final String COLUMNS = """
        id, type, sender_id, sender_name, receiver_id, receiver_name, subject, body, cod_adena, attachments,
        returned_from_id, sent_at, expires_at, read_at, settled_at, sender_deleted_at, receiver_deleted_at,
        expired_at\
        """;
    private static final String INSERT = """
        INSERT INTO mails (id, type, sender_id, sender_name, receiver_id, receiver_name, subject, body, cod_adena,
                           attachments, returned_from_id, sent_at, expires_at, sender_deleted_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\
        """;
    private static final String RECEIVED_VISIBLE =
            "receiver_id = ? AND receiver_deleted_at IS NULL AND expired_at IS NULL";
    private static final String SENT_VISIBLE = "sender_id = ? AND sender_deleted_at IS NULL AND expired_at IS NULL";
    private static final String FIND_RECEIVED =
            "SELECT " + COLUMNS + " FROM mails WHERE id = ? AND " + RECEIVED_VISIBLE;
    private static final String FIND_SENT = "SELECT " + COLUMNS + " FROM mails WHERE id = ? AND " + SENT_VISIBLE;
    // A return is never refused for a full box, so the limit picks the mails still holding attachments first.
    private static final String LIST_RECEIVED = """
        SELECT * FROM (
            SELECT %s FROM mails WHERE %s
            ORDER BY (attachments = 'ATTACHED') DESC, sent_at DESC, id DESC
            LIMIT ?) shown
        ORDER BY sent_at DESC, id DESC        """.formatted(COLUMNS, RECEIVED_VISIBLE);
    private static final String LIST_SENT =
            "SELECT " + COLUMNS + " FROM mails WHERE " + SENT_VISIBLE + " ORDER BY sent_at DESC, id DESC LIMIT ?";
    private static final String COUNT_RECEIVED = "SELECT count(*) FROM mails WHERE " + RECEIVED_VISIBLE;
    private static final String COUNT_SENT = "SELECT count(*) FROM mails WHERE " + SENT_VISIBLE;
    private static final String COUNT_UNREAD =
            "SELECT count(*) FROM mails WHERE " + RECEIVED_VISIBLE + " AND read_at IS NULL";
    private static final String MARK_READ =
            "UPDATE mails SET read_at = ? WHERE id = ? AND receiver_id = ? AND read_at IS NULL";
    private static final String HIDE_RECEIVED = """
        UPDATE mails SET receiver_deleted_at = :now
        WHERE id IN (:ids) AND receiver_id = :owner AND receiver_deleted_at IS NULL AND expired_at IS NULL
          AND attachments <> 'ATTACHED'
        RETURNING id\
        """;
    private static final String HIDE_SENT = """
        UPDATE mails SET sender_deleted_at = :now
        WHERE id IN (:ids) AND sender_id = :owner AND sender_deleted_at IS NULL AND expired_at IS NULL
          AND attachments <> 'ATTACHED'
        RETURNING id\
        """;
    private static final String COUNT_ATTACHED_RECEIVED = """
        SELECT count(*) FROM mails
        WHERE id IN (:ids) AND receiver_id = :owner AND receiver_deleted_at IS NULL AND expired_at IS NULL
          AND attachments = 'ATTACHED'\
        """;
    private static final String COUNT_ATTACHED_SENT = """
        SELECT count(*) FROM mails
        WHERE id IN (:ids) AND sender_id = :owner AND sender_deleted_at IS NULL AND expired_at IS NULL
          AND attachments = 'ATTACHED'\
        """;
    // The sender and the cash on delivery must be what the receiver reserved for: a deletion of the sender drops it.
    private static final String CLAIM = """
        UPDATE mails SET attachments = 'RECEIVED', settled_at = :now
        WHERE id = :id AND receiver_id = :receiver AND attachments = 'ATTACHED' AND expired_at IS NULL
          AND sender_id IS NOT DISTINCT FROM CAST(:sender AS uuid)
          AND cod_adena IS NOT DISTINCT FROM CAST(:cod AS bigint)\
        """;
    private static final String DESTROY = """
        UPDATE mails SET attachments = 'DESTROYED', settled_at = coalesce(settled_at, ?)
        WHERE id = ? AND attachments = ?\
        """;
    private static final String CANCEL = """
        UPDATE mails SET attachments = 'CANCELLED', settled_at = ?, receiver_deleted_at = ?
        WHERE id = ? AND sender_id = ? AND attachments = 'ATTACHED' AND expired_at IS NULL\
        """;
    private static final String SETTLE_RETURNED = """
        UPDATE mails SET attachments = 'RETURNED', settled_at = ?
        WHERE id = ? AND receiver_id = ? AND attachments = 'ATTACHED' AND expired_at IS NULL AND type = 'PLAYER'
          AND returned_from_id IS NULL AND sender_id IS NOT NULL
        RETURNING %s\
        """.formatted(COLUMNS);
    private static final String EXPIRE = """
        UPDATE mails SET attachments = 'EXPIRED', settled_at = ?, expired_at = ?
        WHERE id = ? AND attachments = 'ATTACHED' AND expired_at IS NULL AND expires_at <= ?
        RETURNING %s\
        """.formatted(COLUMNS);
    private static final String EXPIRE_WITHOUT_ATTACHMENTS = """
        UPDATE mails SET expired_at = ?
        WHERE id IN (
            SELECT id FROM mails
            WHERE expired_at IS NULL AND expires_at <= ? AND attachments <> 'ATTACHED'
            ORDER BY expires_at
            LIMIT ?)
          AND expired_at IS NULL
        RETURNING %s\
        """.formatted(COLUMNS);
    private static final String FIND_DUE_ATTACHED = """
        SELECT %s
        FROM mails
        WHERE expired_at IS NULL AND expires_at <= ? AND attachments = 'ATTACHED'
        ORDER BY expires_at
        LIMIT ?\
        """.formatted(COLUMNS);
    private static final String DROP_COD = """
        UPDATE mails SET cod_adena = NULL
        WHERE sender_id = ? AND attachments = 'ATTACHED' AND cod_adena IS NOT NULL\
        """;
    private static final String LOCK_ATTACHED_RECEIVED = """
        SELECT %s
        FROM mails
        WHERE receiver_id = ? AND attachments = 'ATTACHED' AND expired_at IS NULL
        ORDER BY id
        FOR UPDATE\
        """.formatted(COLUMNS);
    private static final String HAS_ATTACHED = """
        SELECT EXISTS (
            SELECT 1 FROM mails m JOIN mail_attachments a ON a.mail_id = m.id
            WHERE m.attachments = 'ATTACHED' AND m.expired_at IS NULL
              AND (m.receiver_id = :character OR m.sender_id = :character)
              AND a.item_template_id IN (:templates))\
        """;
    // Serializes the sends that count one box against the limit; the key space is shared, a collision only waits.
    private static final String LOCK_MAILBOX = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))";

    private final JdbcClient jdbcClient;

    public void insert(MailRow row) {
        jdbcClient
                .sql(INSERT)
                .param(row.id())
                .param(row.type().name())
                .param(row.senderId())
                .param(row.senderName())
                .param(row.receiverId())
                .param(row.receiverName())
                .param(row.subject())
                .param(row.body())
                .param(row.codAdena())
                .param(row.attachments().name())
                .param(row.returnedFromId())
                .param(utc(row.sentAt()))
                .param(utc(row.expiresAt()))
                .param(utc(row.senderDeletedAt()))
                .update();
    }

    public Optional<MailRow> findReceived(UUID mailId, UUID receiverId) {
        return jdbcClient
                .sql(FIND_RECEIVED)
                .param(mailId)
                .param(receiverId)
                .query(MailRepository::row)
                .optional();
    }

    public Optional<MailRow> findSent(UUID mailId, UUID senderId) {
        return jdbcClient
                .sql(FIND_SENT)
                .param(mailId)
                .param(senderId)
                .query(MailRepository::row)
                .optional();
    }

    /**
     * At most {@code limit} mails, newest first; when the box holds more, the mails still holding attachments are the
     * ones shown.
     */
    public List<MailRow> listReceived(UUID receiverId, int limit) {
        return jdbcClient
                .sql(LIST_RECEIVED)
                .param(receiverId)
                .param(limit)
                .query(MailRepository::row)
                .list();
    }

    /** Newest first. */
    public List<MailRow> listSent(UUID senderId, int limit) {
        return jdbcClient
                .sql(LIST_SENT)
                .param(senderId)
                .param(limit)
                .query(MailRepository::row)
                .list();
    }

    public int countReceived(UUID receiverId) {
        return count(COUNT_RECEIVED, receiverId);
    }

    public int countSent(UUID senderId) {
        return count(COUNT_SENT, senderId);
    }

    public int countUnread(UUID receiverId) {
        return count(COUNT_UNREAD, receiverId);
    }

    /** @return {@code false} when the mail was read before */
    public boolean markRead(UUID mailId, UUID receiverId, Instant now) {
        return jdbcClient
                        .sql(MARK_READ)
                        .param(utc(now))
                        .param(mailId)
                        .param(receiverId)
                        .update()
                > 0;
    }

    /** Hides those of the mails without attachments still in them. @return the hidden ones */
    public List<UUID> hideReceived(Collection<UUID> mailIds, UUID receiverId, Instant now) {
        return hide(HIDE_RECEIVED, mailIds, receiverId, now);
    }

    /** Hides those of the mails without attachments still in them. @return the hidden ones */
    public List<UUID> hideSent(Collection<UUID> mailIds, UUID senderId, Instant now) {
        return hide(HIDE_SENT, mailIds, senderId, now);
    }

    public int countAttachedReceived(Collection<UUID> mailIds, UUID receiverId) {
        return countAmong(COUNT_ATTACHED_RECEIVED, mailIds, receiverId);
    }

    public int countAttachedSent(Collection<UUID> mailIds, UUID senderId) {
        return countAmong(COUNT_ATTACHED_SENT, mailIds, senderId);
    }

    /**
     * Settles the attachments as received.
     *
     * @param senderId the sender the receiver expects, {@code null} for none
     * @param codAdena the cash on delivery the receiver reserved, {@code null} for none
     * @return {@code false} when the mail is no longer the receiver's to claim on those terms
     */
    public boolean claim(UUID mailId, UUID receiverId, UUID senderId, Long codAdena, Instant now) {
        return jdbcClient
                        .sql(CLAIM)
                        .param("now", utc(now))
                        .param("id", mailId)
                        .param("receiver", receiverId)
                        .param("sender", senderId)
                        .param("cod", codAdena)
                        .update()
                > 0;
    }

    /**
     * Settles the attachments as {@link AttachmentState#DESTROYED}: from {@code ATTACHED}, or turning a claim or an
     * expiry of the same transaction into it.
     *
     * @return {@code false} when the attachments are not in the state {@code from}
     */
    public boolean destroy(UUID mailId, AttachmentState from, Instant now) {
        return jdbcClient
                        .sql(DESTROY)
                        .param(utc(now))
                        .param(mailId)
                        .param(from.name())
                        .update()
                > 0;
    }

    /** Settles the attachments as cancelled and hides the mail from the receiver. */
    public boolean cancel(UUID mailId, UUID senderId, Instant now) {
        return jdbcClient
                        .sql(CANCEL)
                        .param(utc(now))
                        .param(utc(now))
                        .param(mailId)
                        .param(senderId)
                        .update()
                > 0;
    }

    /** @return the original as it was settled, empty when it cannot be returned */
    public Optional<MailRow> settleReturned(UUID mailId, UUID receiverId, Instant now) {
        return jdbcClient
                .sql(SETTLE_RETURNED)
                .param(utc(now))
                .param(mailId)
                .param(receiverId)
                .query(MailRepository::row)
                .optional();
    }

    public Optional<MailRow> expire(UUID mailId, Instant now) {
        return jdbcClient
                .sql(EXPIRE)
                .param(utc(now))
                .param(utc(now))
                .param(mailId)
                .param(utc(now))
                .query(MailRepository::row)
                .optional();
    }

    /** Hides due mails without attachments in them from both boxes, the oldest first. */
    public List<MailRow> expireWithoutAttachments(Instant now, int limit) {
        return jdbcClient
                .sql(EXPIRE_WITHOUT_ATTACHMENTS)
                .param(utc(now))
                .param(utc(now))
                .param(limit)
                .query(MailRepository::row)
                .list();
    }

    /** The oldest first. */
    public List<MailRow> findDueAttached(Instant now, int limit) {
        return jdbcClient
                .sql(FIND_DUE_ATTACHED)
                .param(utc(now))
                .param(limit)
                .query(MailRepository::row)
                .list();
    }

    /** @return how many mails lost their cash on delivery */
    public int dropCod(UUID senderId) {
        return jdbcClient.sql(DROP_COD).param(senderId).update();
    }

    /** Locks the receiver's mails that still hold attachments until the transaction ends. */
    public List<MailRow> lockAttachedReceived(UUID receiverId) {
        return jdbcClient
                .sql(LOCK_ATTACHED_RECEIVED)
                .param(receiverId)
                .query(MailRepository::row)
                .list();
    }

    public boolean hasAttached(UUID characterId, Collection<Integer> itemTemplateIds) {
        if (itemTemplateIds.isEmpty()) {
            return false;
        }
        return jdbcClient
                .sql(HAS_ATTACHED)
                .param("character", characterId)
                .param("templates", itemTemplateIds)
                .query(Boolean.class)
                .single();
    }

    /** Locks the boxes of the characters until the transaction ends, in one order so two sends cannot deadlock. */
    public void lockMailboxes(UUID first, UUID second) {
        final boolean ordered = first.compareTo(second) <= 0;
        lockMailbox(ordered ? first : second);
        if (!first.equals(second)) {
            lockMailbox(ordered ? second : first);
        }
    }

    private void lockMailbox(UUID characterId) {
        jdbcClient
                .sql(LOCK_MAILBOX)
                .param(characterId)
                .query((rs, rowNum) -> null)
                .list();
    }

    private int count(String sql, UUID characterId) {
        return jdbcClient.sql(sql).param(characterId).query(Integer.class).single();
    }

    private int countAmong(String sql, Collection<UUID> mailIds, UUID ownerId) {
        if (mailIds.isEmpty()) {
            return 0;
        }
        return jdbcClient
                .sql(sql)
                .param("ids", mailIds)
                .param("owner", ownerId)
                .query(Integer.class)
                .single();
    }

    private List<UUID> hide(String sql, Collection<UUID> mailIds, UUID ownerId, Instant now) {
        if (mailIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient
                .sql(sql)
                .param("now", utc(now))
                .param("ids", mailIds)
                .param("owner", ownerId)
                .query(UUID.class)
                .list();
    }

    private static MailRow row(ResultSet rs, int rowNum) throws SQLException {
        return MailRow.builder()
                .id(rs.getObject("id", UUID.class))
                .type(MailType.valueOf(rs.getString("type")))
                .senderId(rs.getObject("sender_id", UUID.class))
                .senderName(rs.getString("sender_name"))
                .receiverId(rs.getObject("receiver_id", UUID.class))
                .receiverName(rs.getString("receiver_name"))
                .subject(rs.getString("subject"))
                .body(rs.getString("body"))
                .codAdena(rs.getObject("cod_adena", Long.class))
                .attachments(AttachmentState.valueOf(rs.getString("attachments")))
                .returnedFromId(rs.getObject("returned_from_id", UUID.class))
                .sentAt(instant(rs, "sent_at"))
                .expiresAt(instant(rs, "expires_at"))
                .readAt(instant(rs, "read_at"))
                .settledAt(instant(rs, "settled_at"))
                .senderDeletedAt(instant(rs, "sender_deleted_at"))
                .receiverDeletedAt(instant(rs, "receiver_deleted_at"))
                .expiredAt(instant(rs, "expired_at"))
                .build();
    }
}
