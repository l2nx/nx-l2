/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.mail;

import static app.l2nx.highfive.repository.LegacyValues.textOrNull;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.network.enums.MessageSenderType;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.mail.model.MailRow;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Player and system mail. Attachments are items with the mail's id ({@code ItemRepository}); they go with the mail.
 */
@Repository
@RequiredArgsConstructor
public class MailRepository {
    private static final int SYSTEM_SENDER = -1;

    private static final String SELECT_MAILS = """
        SELECT id, sender_id, receiver_id, subject, content, expires_at, cod_price, has_attachments, unread,
               deleted_by_sender, deleted_by_receiver, sender_type, returned
        FROM mails
        ORDER BY expires_at\
        """;
    private static final String INSERT_MAIL = """
        INSERT INTO mails (
            id, sender_id, receiver_id, subject, content, expires_at, cod_price, has_attachments, unread,
            deleted_by_sender, deleted_by_receiver, sender_type, returned)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\
        """;
    private static final String UPDATE_READ = "UPDATE mails SET unread = false WHERE id = ?";
    private static final String UPDATE_DELETED_BY_SENDER = "UPDATE mails SET deleted_by_sender = true WHERE id = ?";
    private static final String UPDATE_DELETED_BY_RECEIVER = "UPDATE mails SET deleted_by_receiver = true WHERE id = ?";
    private static final String UPDATE_ATTACHMENTS_REMOVED = "UPDATE mails SET has_attachments = false WHERE id = ?";
    private static final String DELETE_MAIL = "DELETE FROM mails WHERE id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return every mail, the soonest to expire first
     */
    public List<MailRow> findAll() {
        return jdbcClient
                .sql(SELECT_MAILS)
                .query((rs, rowNum) -> {
                    final UUID senderId = rs.getObject("sender_id", UUID.class);
                    return MailRow.builder()
                            .id(registry.handleOf(rs.getObject("id", UUID.class)))
                            .senderId(senderId == null ? SYSTEM_SENDER : registry.handleOf(senderId))
                            .receiverId(registry.handleOf(rs.getObject("receiver_id", UUID.class)))
                            .subject(rs.getString("subject"))
                            .content(rs.getString("content"))
                            .expiration(JdbcTimes.instant(rs, "expires_at"))
                            .reqAdena(rs.getLong("cod_price"))
                            .hasAttachments(rs.getBoolean("has_attachments"))
                            .unread(rs.getBoolean("unread"))
                            .deletedBySender(rs.getBoolean("deleted_by_sender"))
                            .deletedByReceiver(rs.getBoolean("deleted_by_receiver"))
                            .senderType(MessageSenderType.valueOf(rs.getString("sender_type")))
                            .returned(rs.getBoolean("returned"))
                            .build();
                })
                .list();
    }

    /**
     * Stores a new mail; its attachments are written after it, in the same transaction.
     */
    public void insert(MailRow mail) {
        jdbcClient
                .sql(INSERT_MAIL)
                .param(registry.idOf(mail.id()))
                .param(mail.senderId() == SYSTEM_SENDER ? null : References.required(registry, mail.senderId()))
                .param(References.required(registry, mail.receiverId()))
                .param(textOrNull(mail.subject()))
                .param(textOrNull(mail.content()))
                .param(JdbcTimes.utc(mail.expiration()))
                .param(mail.reqAdena() == 0 ? null : mail.reqAdena())
                .param(mail.hasAttachments())
                .param(mail.unread())
                .param(mail.deletedBySender())
                .param(mail.deletedByReceiver())
                .param(mail.senderType().name())
                .param(mail.returned())
                .update();
    }

    public void markRead(int mailId) {
        registry.findId(mailId)
                .ifPresent(id -> jdbcClient.sql(UPDATE_READ).param(id).update());
    }

    public void markDeletedBySender(int mailId) {
        registry.findId(mailId)
                .ifPresent(
                        id -> jdbcClient.sql(UPDATE_DELETED_BY_SENDER).param(id).update());
    }

    public void markDeletedByReceiver(int mailId) {
        registry.findId(mailId)
                .ifPresent(id ->
                        jdbcClient.sql(UPDATE_DELETED_BY_RECEIVER).param(id).update());
    }

    public void markAttachmentsRemoved(int mailId) {
        registry.findId(mailId)
                .ifPresent(id ->
                        jdbcClient.sql(UPDATE_ATTACHMENTS_REMOVED).param(id).update());
    }

    public void delete(int mailId) {
        registry.findId(mailId)
                .ifPresent(id -> jdbcClient.sql(DELETE_MAIL).param(id).update());
    }
}
