/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.territory;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.territory.model.TerritoryRegistrantType;
import app.l2nx.highfive.repository.territory.model.TerritoryRegistration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TerritoryRegistrationRepository {
    private static final String SELECT_ALL = """
        SELECT castle_id, registrant_id, registrant_type
        FROM territory_registrations
        ORDER BY castle_id, registrant_id\
        """;
    private static final String INSERT = """
        INSERT INTO territory_registrations (castle_id, registrant_id, registrant_type) VALUES (?, ?, ?)\
        """;
    private static final String DELETE =
            "DELETE FROM territory_registrations WHERE castle_id = ? AND registrant_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<TerritoryRegistration> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> TerritoryRegistration.builder()
                        .castleId(rs.getInt("castle_id"))
                        .registrantId(registry.handleOf(rs.getObject("registrant_id", UUID.class)))
                        .registrantType(TerritoryRegistrantType.valueOf(rs.getString("registrant_type")))
                        .build())
                .list();
    }

    /**
     * @param registrantId clan handle for {@link TerritoryRegistrantType#CLAN}, character handle for a mercenary
     */
    public void insert(int castleId, int registrantId, TerritoryRegistrantType registrantType) {
        jdbcClient
                .sql(INSERT)
                .param(castleId)
                .param(References.required(registry, registrantId))
                .param(registrantType.name())
                .update();
    }

    public void delete(int castleId, int registrantId) {
        registry.findId(registrantId)
                .ifPresent(
                        id -> jdbcClient.sql(DELETE).param(castleId).param(id).update());
    }
}
