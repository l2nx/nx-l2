/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import app.l2nx.highfive.repository.residence.model.CastleTrapUpgrade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CastleTrapUpgradeRepository {
    private static final String SELECT_BY_CASTLE =
            "SELECT tower_index, trap_level FROM castle_trap_upgrades WHERE castle_id = ? ORDER BY tower_index";
    private static final String UPSERT = """
        INSERT INTO castle_trap_upgrades (castle_id, tower_index, trap_level) VALUES (?, ?, ?)
        ON CONFLICT (castle_id, tower_index) DO UPDATE SET trap_level = EXCLUDED.trap_level\
        """;
    private static final String DELETE_BY_CASTLE = "DELETE FROM castle_trap_upgrades WHERE castle_id = ?";

    private final JdbcClient jdbcClient;

    public List<CastleTrapUpgrade> findByCastle(int castleId) {
        return jdbcClient
                .sql(SELECT_BY_CASTLE)
                .param(castleId)
                .query((rs, rowNum) -> CastleTrapUpgrade.builder()
                        .towerIndex(rs.getInt("tower_index"))
                        .trapLevel(rs.getInt("trap_level"))
                        .build())
                .list();
    }

    public void save(int castleId, CastleTrapUpgrade upgrade) {
        jdbcClient
                .sql(UPSERT)
                .param(castleId)
                .param(upgrade.towerIndex())
                .param(upgrade.trapLevel())
                .update();
    }

    public void deleteByCastle(int castleId) {
        jdbcClient.sql(DELETE_BY_CASTLE).param(castleId).update();
    }
}
