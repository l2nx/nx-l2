/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import static app.l2nx.highfive.repository.References.requiredOrNull;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.residence.model.SiegableClanHallState;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A siegable hall without a row has no owner and no stored siege date, so the hall schedules its next siege.
 */
@Repository
@RequiredArgsConstructor
public class SiegableClanHallStateRepository {
    private static final String SELECT_ALL = """
        SELECT clan_hall_id, owner_clan_id, next_siege_at
        FROM siegable_clan_hall_states
        ORDER BY clan_hall_id\
        """;
    private static final String UPSERT = """
        INSERT INTO siegable_clan_hall_states (clan_hall_id, owner_clan_id, next_siege_at) VALUES (?, ?, ?)
        ON CONFLICT (clan_hall_id) DO UPDATE
        SET owner_clan_id = EXCLUDED.owner_clan_id, next_siege_at = EXCLUDED.next_siege_at\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<SiegableClanHallState> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> {
                    final UUID ownerId = rs.getObject("owner_clan_id", UUID.class);
                    return SiegableClanHallState.builder()
                            .clanHallId(rs.getInt("clan_hall_id"))
                            .ownerClanId(ownerId == null ? 0 : registry.handleOf(ownerId))
                            .nextSiege(JdbcTimes.instant(rs, "next_siege_at"))
                            .build();
                })
                .list();
    }

    /**
     * @param state {@code ownerClanId} and {@code nextSiege} of {@code 0} store no value
     */
    public void save(SiegableClanHallState state) {
        jdbcClient
                .sql(UPSERT)
                .param(state.clanHallId())
                .param(requiredOrNull(registry, state.ownerClanId()))
                .param(JdbcTimes.utc(state.nextSiege()))
                .update();
    }
}
