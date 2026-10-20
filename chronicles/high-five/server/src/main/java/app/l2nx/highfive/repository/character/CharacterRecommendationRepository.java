/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterRecommendation;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterRecommendationRepository {
    private static final String SELECT_RECOMMENDATION = """
        SELECT received, left_to_give, bonus_time_left_ms
        FROM character_recommendations
        WHERE character_id = ?\
        """;
    private static final String UPSERT_RECOMMENDATION = """
        INSERT INTO character_recommendations (character_id, received, left_to_give, bonus_time_left_ms)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (character_id) DO UPDATE
        SET received = EXCLUDED.received, left_to_give = EXCLUDED.left_to_give,
            bonus_time_left_ms = EXCLUDED.bonus_time_left_ms\
        """;
    private static final String UPSERT_COUNTS = """
        INSERT INTO character_recommendations (character_id, received, left_to_give)
        VALUES (?, ?, ?)
        ON CONFLICT (character_id) DO UPDATE SET received = EXCLUDED.received, left_to_give = EXCLUDED.left_to_give\
        """;
    // One statement for both legacy branches: received <= 20 dropped to 0, above that lost 20.
    private static final String RESET_DAILY = """
        UPDATE character_recommendations
        SET left_to_give = ?, bonus_time_left_ms = ?, received = GREATEST(received - ?, 0)\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public Optional<CharacterRecommendation> find(int objectId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_RECOMMENDATION)
                        .param(id)
                        .query((rs, rowNum) -> CharacterRecommendation.builder()
                                .received(rs.getInt("received"))
                                .leftToGive(rs.getInt("left_to_give"))
                                .bonusTimeLeftMs(rs.getLong("bonus_time_left_ms"))
                                .build())
                        .optional());
    }

    public void save(int objectId, CharacterRecommendation recommendation) {
        jdbcClient
                .sql(UPSERT_RECOMMENDATION)
                .param(References.required(registry, objectId))
                .param(recommendation.received())
                .param(recommendation.leftToGive())
                .param(recommendation.bonusTimeLeftMs())
                .update();
    }

    /**
     * Stores the recommendation counts and keeps the stored bonus time.
     */
    public void saveCounts(int objectId, int received, int leftToGive) {
        jdbcClient
                .sql(UPSERT_COUNTS)
                .param(References.required(registry, objectId))
                .param(received)
                .param(leftToGive)
                .update();
    }

    /**
     * The daily reset of every stored character: new recommendations to give, a new bonus time, and the received
     * count lowered by {@code receivedDecrease}, not below 0.
     */
    public void resetDaily(int leftToGive, long bonusTimeLeftMs, int receivedDecrease) {
        jdbcClient
                .sql(RESET_DAILY)
                .param(leftToGive)
                .param(bonusTimeLeftMs)
                .param(receivedDecrease)
                .update();
    }
}
