/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.mail;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.mail.model.CustomMail;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The delivery queue of mails written by outside tools.
 */
@Repository
@RequiredArgsConstructor
public class CustomMailRepository {
    private static final String SELECT_MAILS = """
        SELECT id, character_id, subject, content, items
        FROM custom_mails
        ORDER BY id\
        """;
    private static final String DELETE_MAIL = "DELETE FROM custom_mails WHERE id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CustomMail> findAll() {
        return jdbcClient
                .sql(SELECT_MAILS)
                .query((rs, rowNum) -> CustomMail.builder()
                        .id(rs.getLong("id"))
                        .receiverId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                        .subject(rs.getString("subject"))
                        .content(rs.getString("content"))
                        .items(rs.getString("items"))
                        .build())
                .list();
    }

    public void delete(long id) {
        jdbcClient.sql(DELETE_MAIL).param(id).update();
    }
}
