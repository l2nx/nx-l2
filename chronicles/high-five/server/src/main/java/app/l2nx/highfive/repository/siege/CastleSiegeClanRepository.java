/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.siege;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.mechanics.siege.SiegeClanType;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.siege.model.CastleSiegeClan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CastleSiegeClanRepository {
    private static final String SELECT_BY_CASTLE =
            "SELECT clan_id, side FROM castle_siege_clans WHERE castle_id = ? ORDER BY clan_id";
    private static final String EXISTS =
            "SELECT EXISTS (SELECT 1 FROM castle_siege_clans WHERE castle_id = ? AND clan_id = ?)";
    private static final String INSERT = "INSERT INTO castle_siege_clans (castle_id, clan_id, side) VALUES (?, ?, ?)";
    private static final String UPDATE_SIDE =
            "UPDATE castle_siege_clans SET side = ? WHERE castle_id = ? AND clan_id = ?";
    private static final String DELETE = "DELETE FROM castle_siege_clans WHERE castle_id = ? AND clan_id = ?";
    private static final String DELETE_BY_CASTLE = "DELETE FROM castle_siege_clans WHERE castle_id = ?";
    private static final String DELETE_BY_CASTLE_AND_SIDE =
            "DELETE FROM castle_siege_clans WHERE castle_id = ? AND side = ?";
    private static final String DELETE_BY_CLAN = "DELETE FROM castle_siege_clans WHERE clan_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CastleSiegeClan> findByCastle(int castleId) {
        return jdbcClient
                .sql(SELECT_BY_CASTLE)
                .param(castleId)
                .query((rs, rowNum) -> CastleSiegeClan.builder()
                        .clanId(registry.handleOf(rs.getObject("clan_id", UUID.class)))
                        .side(SiegeClanType.valueOf(rs.getString("side")))
                        .build())
                .list();
    }

    public boolean exists(int castleId, int clanId) {
        final Optional<UUID> id = registry.findId(clanId);
        return id.isPresent()
                && jdbcClient
                        .sql(EXISTS)
                        .param(castleId)
                        .param(id.get())
                        .query(Boolean.class)
                        .single();
    }

    public void insert(int castleId, int clanId, SiegeClanType side) {
        jdbcClient
                .sql(INSERT)
                .param(castleId)
                .param(References.required(registry, clanId))
                .param(side.name())
                .update();
    }

    public void updateSide(int castleId, int clanId, SiegeClanType side) {
        registry.findId(clanId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_SIDE)
                        .param(side.name())
                        .param(castleId)
                        .param(id)
                        .update());
    }

    public void delete(int castleId, int clanId) {
        registry.findId(clanId)
                .ifPresent(
                        id -> jdbcClient.sql(DELETE).param(castleId).param(id).update());
    }

    public void deleteByCastle(int castleId) {
        jdbcClient.sql(DELETE_BY_CASTLE).param(castleId).update();
    }

    public void deleteByCastleAndSide(int castleId, SiegeClanType side) {
        jdbcClient
                .sql(DELETE_BY_CASTLE_AND_SIDE)
                .param(castleId)
                .param(side.name())
                .update();
    }

    public void deleteByClan(int clanId) {
        registry.findId(clanId)
                .ifPresent(id -> jdbcClient.sql(DELETE_BY_CLAN).param(id).update());
    }
}
