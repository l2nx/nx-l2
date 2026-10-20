/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.world.model.FishingChampionshipEntry;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class FishingChampionshipRepository {
    private static final int ENTRY = 0;
    private static final int UNCLAIMED = 1;
    private static final int REWARDED = 2;

    private static final String SELECT_ALL = """
        SELECT e.character_id, c.name, e.fish_length, e.winner, e.rewarded
        FROM fishing_championship_entries e
        JOIN characters c ON c.id = e.character_id\
        """;
    private static final String DELETE_ALL = "DELETE FROM fishing_championship_entries";
    private static final String INSERT = """
        INSERT INTO fishing_championship_entries (character_id, winner, fish_length, rewarded)
        VALUES (?, ?, ?, ?)\
        """;

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<FishingChampionshipEntry> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> {
                    final int rewardType;
                    if (!rs.getBoolean("winner")) {
                        rewardType = ENTRY;
                    } else {
                        rewardType = rs.getBoolean("rewarded") ? REWARDED : UNCLAIMED;
                    }
                    return FishingChampionshipEntry.builder()
                            .objectId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                            .characterName(rs.getString("name"))
                            .fishLength(rs.getDouble("fish_length"))
                            .rewardType(rewardType)
                            .build();
                })
                .list();
    }

    /**
     * {@code characterName} of the entries is ignored.
     */
    @Transactional
    public void replaceAll(Collection<FishingChampionshipEntry> entries) {
        jdbcClient.sql(DELETE_ALL).update();
        jdbcTemplate.batchUpdate(
                INSERT,
                entries.stream()
                        // A character deleted since it entered the championship has no row to reference.
                        .flatMap(entry -> registry.findId(entry.objectId()).stream()
                                .map(id -> new Object[] {
                                    id, entry.rewardType() != ENTRY, entry.fishLength(), entry.rewardType() == REWARDED
                                }))
                        .toList());
    }
}
