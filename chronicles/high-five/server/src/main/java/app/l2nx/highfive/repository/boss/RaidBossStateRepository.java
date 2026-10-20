/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.boss;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.repository.boss.model.RaidBossState;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RaidBossStateRepository {
    private static final String SELECT_ALL =
            "SELECT npc_id, respawn_at, current_hp, current_mp FROM raid_boss_states ORDER BY npc_id";
    private static final String UPSERT = """
        INSERT INTO raid_boss_states (npc_id, respawn_at, current_hp, current_mp)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (npc_id) DO UPDATE
        SET respawn_at = EXCLUDED.respawn_at, current_hp = EXCLUDED.current_hp, current_mp = EXCLUDED.current_mp\
        """;

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    /**
     * @return the stored state of every boss that was ever saved; a boss without a row was never killed
     */
    public List<RaidBossState> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> RaidBossState.builder()
                        .npcId(rs.getInt("npc_id"))
                        .respawnTime(JdbcTimes.instant(rs, "respawn_at"))
                        .currentHp(rs.getDouble("current_hp"))
                        .currentMp(rs.getDouble("current_mp"))
                        .build())
                .list();
    }

    public void saveAll(Collection<RaidBossState> states) {
        jdbcTemplate.batchUpdate(
                UPSERT,
                states.stream()
                        .map(state -> new Object[] {
                            state.npcId(), JdbcTimes.utc(state.respawnTime()), state.currentHp(), state.currentMp()
                        })
                        .toList());
    }
}
