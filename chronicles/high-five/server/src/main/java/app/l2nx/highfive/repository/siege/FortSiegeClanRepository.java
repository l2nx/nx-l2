/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.siege;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FortSiegeClanRepository {
    private static final String SELECT_BY_FORT =
            "SELECT clan_id FROM fort_siege_clans WHERE fort_id = ? ORDER BY clan_id";
    private static final String EXISTS =
            "SELECT EXISTS (SELECT 1 FROM fort_siege_clans WHERE fort_id = ? AND clan_id = ?)";
    private static final String INSERT = "INSERT INTO fort_siege_clans (fort_id, clan_id) VALUES (?, ?)";
    private static final String DELETE = "DELETE FROM fort_siege_clans WHERE fort_id = ? AND clan_id = ?";
    private static final String DELETE_BY_FORT = "DELETE FROM fort_siege_clans WHERE fort_id = ?";
    private static final String DELETE_BY_CLAN = "DELETE FROM fort_siege_clans WHERE clan_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    /**
     * @return clan handles
     */
    public List<Integer> findClanIdsByFort(int fortId) {
        return jdbcClient
                .sql(SELECT_BY_FORT)
                .param(fortId)
                .query((rs, rowNum) -> registry.handleOf(rs.getObject("clan_id", UUID.class)))
                .list();
    }

    public boolean exists(int fortId, int clanId) {
        final Optional<UUID> id = registry.findId(clanId);
        return id.isPresent()
                && jdbcClient
                        .sql(EXISTS)
                        .param(fortId)
                        .param(id.get())
                        .query(Boolean.class)
                        .single();
    }

    public void insert(int fortId, int clanId) {
        jdbcClient
                .sql(INSERT)
                .param(fortId)
                .param(References.required(registry, clanId))
                .update();
    }

    public void delete(int fortId, int clanId) {
        registry.findId(clanId)
                .ifPresent(id -> jdbcClient.sql(DELETE).param(fortId).param(id).update());
    }

    public void deleteByFort(int fortId) {
        jdbcClient.sql(DELETE_BY_FORT).param(fortId).update();
    }

    public void deleteByClan(int clanId) {
        registry.findId(clanId)
                .ifPresent(id -> jdbcClient.sql(DELETE_BY_CLAN).param(id).update());
    }
}
