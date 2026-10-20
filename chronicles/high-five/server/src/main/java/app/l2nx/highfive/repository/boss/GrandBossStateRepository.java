/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.boss;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.repository.boss.model.GrandBossState;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GrandBossStateRepository {
    private static final String SELECT_ALL = """
        SELECT npc_id, status, respawn_at, current_hp, current_mp, x, y, z, heading
        FROM grand_boss_states
        ORDER BY npc_id\
        """;
    private static final String UPSERT = """
        INSERT INTO grand_boss_states (npc_id, status, respawn_at, current_hp, current_mp, x, y, z, heading)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (npc_id) DO UPDATE
        SET status = EXCLUDED.status, respawn_at = EXCLUDED.respawn_at,
            current_hp = EXCLUDED.current_hp, current_mp = EXCLUDED.current_mp,
            x = EXCLUDED.x, y = EXCLUDED.y, z = EXCLUDED.z, heading = EXCLUDED.heading\
        """;
    // A boss saved for the first time by status only spawns at the datapack point at full HP/MP.
    private static final String UPSERT_STATUS = """
        INSERT INTO grand_boss_states (npc_id, status)
        VALUES (?, ?)
        ON CONFLICT (npc_id) DO UPDATE SET status = EXCLUDED.status\
        """;

    private final JdbcClient jdbcClient;

    public List<GrandBossState> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> GrandBossState.builder()
                        .npcId(rs.getInt("npc_id"))
                        .status(rs.getInt("status"))
                        .respawnTime(JdbcTimes.instant(rs, "respawn_at"))
                        .currentHp(rs.getObject("current_hp", Double.class))
                        .currentMp(rs.getObject("current_mp", Double.class))
                        .x(rs.getObject("x", Integer.class))
                        .y(rs.getObject("y", Integer.class))
                        .z(rs.getObject("z", Integer.class))
                        .heading(rs.getObject("heading", Integer.class))
                        .build())
                .list();
    }

    public void save(GrandBossState state) {
        jdbcClient
                .sql(UPSERT)
                .param(state.npcId())
                .param(state.status())
                .param(JdbcTimes.utc(state.respawnTime()))
                .param(state.currentHp())
                .param(state.currentMp())
                .param(state.x())
                .param(state.y())
                .param(state.z())
                .param(state.heading())
                .update();
    }

    /**
     * Writes the status alone, keeping the stored respawn time, HP/MP and position of an existing row.
     */
    public void saveStatus(int npcId, int status) {
        jdbcClient.sql(UPSERT_STATUS).param(npcId).param(status).update();
    }
}
