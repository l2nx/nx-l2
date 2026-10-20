/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.olympiad;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.olympiad.model.OlympiadFight;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OlympiadFightRepository {
    private static final String INSERT = """
        INSERT INTO olympiad_fights (
            first_character_id, second_character_id, first_class_id, second_class_id, winner, started_at,
            duration_ms, classed)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)\
        """;
    private static final String SELECT_BY_CHARACTER_BEFORE = """
        SELECT first_character_id, second_character_id, first_class_id, second_class_id, winner, started_at,
               duration_ms, classed
        FROM olympiad_fights
        WHERE (first_character_id = ? OR second_character_id = ?) AND started_at < ?
        ORDER BY started_at\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public void insert(OlympiadFight fight) {
        jdbcClient
                .sql(INSERT)
                .param(References.required(registry, fight.firstObjectId()))
                .param(References.required(registry, fight.secondObjectId()))
                .param(fight.firstClassId())
                .param(fight.secondClassId())
                .param(fight.winner() == 0 ? null : fight.winner())
                .param(JdbcTimes.utc(fight.startedAt()))
                .param(fight.durationMs())
                .param(fight.classed())
                .update();
    }

    /**
     * @param before exclusive
     */
    public List<OlympiadFight> findByCharacterStartedBefore(int objectId, Instant before) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_BY_CHARACTER_BEFORE)
                        .param(id)
                        .param(id)
                        .param(JdbcTimes.utc(before))
                        .query((rs, rowNum) -> OlympiadFight.builder()
                                .firstObjectId(registry.handleOf(rs.getObject("first_character_id", UUID.class)))
                                .secondObjectId(registry.handleOf(rs.getObject("second_character_id", UUID.class)))
                                .firstClassId(rs.getInt("first_class_id"))
                                .secondClassId(rs.getInt("second_class_id"))
                                .winner(rs.getInt("winner"))
                                .startedAt(JdbcTimes.instant(rs, "started_at"))
                                .durationMs(rs.getLong("duration_ms"))
                                .classed(rs.getBoolean("classed"))
                                .build())
                        .list())
                .orElseGet(List::of);
    }
}
