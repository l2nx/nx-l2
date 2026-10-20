/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import app.l2nx.highfive.repository.residence.model.CastleDoorUpgrade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CastleDoorUpgradeRepository {
    private static final String SELECT_BY_CASTLE =
            "SELECT door_id, hp_ratio FROM castle_door_upgrades WHERE castle_id = ? ORDER BY door_id";
    private static final String UPSERT = """
        INSERT INTO castle_door_upgrades (door_id, castle_id, hp_ratio) VALUES (?, ?, ?)
        ON CONFLICT (door_id) DO UPDATE SET castle_id = EXCLUDED.castle_id, hp_ratio = EXCLUDED.hp_ratio\
        """;
    private static final String DELETE_BY_CASTLE = "DELETE FROM castle_door_upgrades WHERE castle_id = ?";

    private final JdbcClient jdbcClient;

    public List<CastleDoorUpgrade> findByCastle(int castleId) {
        return jdbcClient
                .sql(SELECT_BY_CASTLE)
                .param(castleId)
                .query((rs, rowNum) -> CastleDoorUpgrade.builder()
                        .doorId(rs.getInt("door_id"))
                        .hpRatio(rs.getInt("hp_ratio"))
                        .build())
                .list();
    }

    public void save(int castleId, CastleDoorUpgrade upgrade) {
        jdbcClient
                .sql(UPSERT)
                .param(upgrade.doorId())
                .param(castleId)
                .param(upgrade.hpRatio())
                .update();
    }

    public void deleteByCastle(int castleId) {
        jdbcClient.sql(DELETE_BY_CASTLE).param(castleId).update();
    }
}
