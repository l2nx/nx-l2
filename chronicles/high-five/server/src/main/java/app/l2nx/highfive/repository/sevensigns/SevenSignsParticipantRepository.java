/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.sevensigns;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.sevensigns.model.SevenSignsParticipant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SevenSignsParticipantRepository {
    // Index = the SevenSigns.SEAL_* constant; SEAL_NULL (0) is NULL.
    private static final List<String> SEALS = List.of("AVARICE", "GNOSIS", "STRIFE");

    private static final String SELECT_ALL = """
        SELECT character_id, cabal, seal, red_stones, green_stones, blue_stones, ancient_adena_amount,
               contribution_score
        FROM seven_signs_participants\
        """;
    private static final String UPSERT = """
        INSERT INTO seven_signs_participants (
            character_id, cabal, seal, red_stones, green_stones, blue_stones, ancient_adena_amount,
            contribution_score)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (character_id) DO UPDATE
        SET cabal = EXCLUDED.cabal, seal = EXCLUDED.seal, red_stones = EXCLUDED.red_stones,
            green_stones = EXCLUDED.green_stones, blue_stones = EXCLUDED.blue_stones,
            ancient_adena_amount = EXCLUDED.ancient_adena_amount, contribution_score = EXCLUDED.contribution_score\
        """;

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<SevenSignsParticipant> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> {
                    final String seal = rs.getString("seal");
                    return SevenSignsParticipant.builder()
                            .objectId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                            .cabal(CabalColumns.toShortName(rs.getString("cabal")))
                            .seal(seal == null ? 0 : SEALS.indexOf(seal) + 1)
                            .redStones(rs.getInt("red_stones"))
                            .greenStones(rs.getInt("green_stones"))
                            .blueStones(rs.getInt("blue_stones"))
                            .ancientAdenaAmount(rs.getLong("ancient_adena_amount"))
                            .contributionScore(rs.getLong("contribution_score"))
                            .build();
                })
                .list();
    }

    public void save(SevenSignsParticipant participant) {
        jdbcClient
                .sql(UPSERT)
                .params(row(References.required(registry, participant.objectId()), participant))
                .update();
    }

    /**
     * A participant whose character was deleted is skipped.
     */
    public void saveAll(Collection<SevenSignsParticipant> participants) {
        jdbcTemplate.batchUpdate(
                UPSERT,
                participants.stream()
                        .flatMap(participant ->
                                registry.findId(participant.objectId()).stream().map(id -> row(id, participant)))
                        .toList());
    }

    private Object[] row(UUID id, SevenSignsParticipant participant) {
        return new Object[] {
            id,
            CabalColumns.fromShortName(participant.cabal()),
            participant.seal() == 0 ? null : SEALS.get(participant.seal() - 1),
            participant.redStones(),
            participant.greenStones(),
            participant.blueStones(),
            Math.round(participant.ancientAdenaAmount()),
            Math.round(participant.contributionScore())
        };
    }
}
