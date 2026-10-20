/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import app.l2nx.highfive.repository.residence.model.FortDoorUpgrade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FortDoorUpgradeRepository {
    private static final String SELECT_BY_FORT =
            "SELECT door_id, hp, p_def, m_def FROM fort_door_upgrades WHERE fort_id = ? ORDER BY door_id";
    // Fort.upgradeDoor saves on every load too, so a door already upgraded is overwritten, not duplicated.
    private static final String UPSERT = """
        INSERT INTO fort_door_upgrades (door_id, fort_id, hp, p_def, m_def) VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (door_id) DO UPDATE
        SET fort_id = EXCLUDED.fort_id, hp = EXCLUDED.hp, p_def = EXCLUDED.p_def, m_def = EXCLUDED.m_def\
        """;
    private static final String DELETE_BY_FORT = "DELETE FROM fort_door_upgrades WHERE fort_id = ?";

    private final JdbcClient jdbcClient;

    public List<FortDoorUpgrade> findByFort(int fortId) {
        return jdbcClient
                .sql(SELECT_BY_FORT)
                .param(fortId)
                .query((rs, rowNum) -> FortDoorUpgrade.builder()
                        .doorId(rs.getInt("door_id"))
                        .hp(rs.getInt("hp"))
                        .pDef(rs.getInt("p_def"))
                        .mDef(rs.getInt("m_def"))
                        .build())
                .list();
    }

    public void save(int fortId, FortDoorUpgrade upgrade) {
        jdbcClient
                .sql(UPSERT)
                .param(upgrade.doorId())
                .param(fortId)
                .param(upgrade.hp())
                .param(upgrade.pDef())
                .param(upgrade.mDef())
                .update();
    }

    public void deleteByFort(int fortId) {
        jdbcClient.sql(DELETE_BY_FORT).param(fortId).update();
    }
}
