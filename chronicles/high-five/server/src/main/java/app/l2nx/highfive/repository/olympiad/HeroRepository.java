/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.olympiad;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.olympiad.model.HeroRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class HeroRepository {
    private static final String SELECT_ALL = """
        SELECT h.character_id, c.name, h.class_id, h.hero_count, h.played, h.claimed
        FROM heroes h
        JOIN characters c ON c.id = h.character_id\
        """;
    private static final String SELECT_PLAYED = SELECT_ALL + " WHERE h.played";
    private static final String RESET_PLAYED = "UPDATE heroes SET played = false WHERE played";
    private static final String UPSERT = """
        INSERT INTO heroes (character_id, class_id, hero_count, played, claimed)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (character_id) DO UPDATE
        SET class_id = EXCLUDED.class_id, hero_count = EXCLUDED.hero_count, played = EXCLUDED.played,
            claimed = EXCLUDED.claimed\
        """;
    private static final String SELECT_MESSAGE = "SELECT message FROM heroes WHERE character_id = ?";
    private static final String UPDATE_MESSAGE = "UPDATE heroes SET message = ? WHERE character_id = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    /**
     * @return every character that was ever hero
     */
    public List<HeroRow> findAll() {
        return find(SELECT_ALL);
    }

    /**
     * @return the heroes of the current period
     */
    public List<HeroRow> findPlayed() {
        return find(SELECT_PLAYED);
    }

    public void resetPlayed() {
        jdbcClient.sql(RESET_PLAYED).update();
    }

    /**
     * Inserts new heroes and updates stored ones; the hero message is kept. A hero whose character was deleted is
     * skipped.
     */
    public void saveAll(Collection<HeroRow> heroes) {
        jdbcTemplate.batchUpdate(
                UPSERT,
                heroes.stream()
                        .flatMap(hero -> registry.findId(hero.objectId()).stream()
                                .map(id ->
                                        new Object[] {id, hero.classId(), hero.count(), hero.played(), hero.claimed()}))
                        .toList());
    }

    /**
     * @return the hero's message, empty when the character is no hero or wrote none
     */
    public Optional<String> findMessage(int objectId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_MESSAGE)
                        .param(id)
                        .query((rs, rowNum) -> Optional.ofNullable(rs.getString("message")))
                        .optional()
                        .flatMap(message -> message));
    }

    /**
     * Stores the message of a stored hero; a blank message clears it.
     */
    public void updateMessage(int objectId, String message) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_MESSAGE)
                        .param(StringUtils.isBlank(message) ? null : message)
                        .param(id)
                        .update());
    }

    private List<HeroRow> find(String sql) {
        return jdbcClient
                .sql(sql)
                .query((rs, rowNum) -> HeroRow.builder()
                        .objectId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                        .characterName(rs.getString("name"))
                        .classId(rs.getInt("class_id"))
                        .count(rs.getInt("hero_count"))
                        .played(rs.getBoolean("played"))
                        .claimed(rs.getBoolean("claimed"))
                        .build())
                .list();
    }
}
