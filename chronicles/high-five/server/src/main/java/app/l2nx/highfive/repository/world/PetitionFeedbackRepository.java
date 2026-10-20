/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.world.model.PetitionFeedback;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PetitionFeedbackRepository {
    private static final String INSERT = """
        INSERT INTO petition_feedbacks (character_id, gm_character_id, rating, message, created_at)
        VALUES (?, ?, ?, ?, ?)\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * A blank message is stored as none.
     */
    public void insert(PetitionFeedback feedback) {
        jdbcClient
                .sql(INSERT)
                .param(References.required(registry, feedback.objectId()))
                .param(References.required(registry, feedback.gmObjectId()))
                .param(feedback.rating())
                .param(StringUtils.isBlank(feedback.message()) ? null : feedback.message())
                .param(JdbcTimes.utc(feedback.createdAt()))
                .update();
    }
}
