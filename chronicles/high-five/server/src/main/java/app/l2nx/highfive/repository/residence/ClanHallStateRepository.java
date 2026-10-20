/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import static app.l2nx.highfive.repository.References.requiredOrNull;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.residence.model.ClanHallState;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * State of the auctionable clan halls. A hall without a row is free and unpaid.
 */
@Repository
@RequiredArgsConstructor
public class ClanHallStateRepository {
    private static final String SELECT_ALL =
            "SELECT clan_hall_id, owner_clan_id, paid_until, paid FROM clan_hall_states ORDER BY clan_hall_id";
    private static final String UPSERT = """
        INSERT INTO clan_hall_states (clan_hall_id, owner_clan_id, paid_until, paid) VALUES (?, ?, ?, ?)
        ON CONFLICT (clan_hall_id) DO UPDATE
        SET owner_clan_id = EXCLUDED.owner_clan_id, paid_until = EXCLUDED.paid_until, paid = EXCLUDED.paid\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<ClanHallState> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> {
                    final UUID ownerId = rs.getObject("owner_clan_id", UUID.class);
                    return ClanHallState.builder()
                            .clanHallId(rs.getInt("clan_hall_id"))
                            .ownerClanId(ownerId == null ? 0 : registry.handleOf(ownerId))
                            .paidUntil(JdbcTimes.instant(rs, "paid_until"))
                            .paid(rs.getBoolean("paid"))
                            .build();
                })
                .list();
    }

    /**
     * @param state {@code ownerClanId} and {@code paidUntil} of {@code 0} store no value
     */
    public void save(ClanHallState state) {
        jdbcClient
                .sql(UPSERT)
                .param(state.clanHallId())
                .param(requiredOrNull(registry, state.ownerClanId()))
                .param(JdbcTimes.utc(state.paidUntil()))
                .param(state.paid())
                .update();
    }
}
