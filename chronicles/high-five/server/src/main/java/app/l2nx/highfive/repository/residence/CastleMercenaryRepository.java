/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import app.l2nx.highfive.repository.residence.model.CastleMercenary;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CastleMercenaryRepository {
    private static final String SELECT_ALL =
            "SELECT castle_id, npc_id, x, y, z, heading FROM castle_mercenaries ORDER BY id";
    private static final String SELECT_BY_CASTLE =
            "SELECT castle_id, npc_id, x, y, z, heading FROM castle_mercenaries WHERE castle_id = ? ORDER BY id";
    private static final String INSERT =
            "INSERT INTO castle_mercenaries (castle_id, npc_id, x, y, z, heading) VALUES (?, ?, ?, ?, ?, ?)";
    // Mobius identifies a mercenary by its npc and spot: a picked-up ticket knows nothing else.
    private static final String DELETE_AT =
            "DELETE FROM castle_mercenaries WHERE npc_id = ? AND x = ? AND y = ? AND z = ?";
    private static final String DELETE_BY_CASTLE = "DELETE FROM castle_mercenaries WHERE castle_id = ?";

    private static final RowMapper<CastleMercenary> MAPPER = (rs, rowNum) -> CastleMercenary.builder()
            .castleId(rs.getInt("castle_id"))
            .npcId(rs.getInt("npc_id"))
            .x(rs.getInt("x"))
            .y(rs.getInt("y"))
            .z(rs.getInt("z"))
            .heading(rs.getInt("heading"))
            .build();

    private final JdbcClient jdbcClient;

    public List<CastleMercenary> findAll() {
        return jdbcClient.sql(SELECT_ALL).query(MAPPER).list();
    }

    public List<CastleMercenary> findByCastle(int castleId) {
        return jdbcClient.sql(SELECT_BY_CASTLE).param(castleId).query(MAPPER).list();
    }

    public void insert(CastleMercenary mercenary) {
        jdbcClient
                .sql(INSERT)
                .param(mercenary.castleId())
                .param(mercenary.npcId())
                .param(mercenary.x())
                .param(mercenary.y())
                .param(mercenary.z())
                .param(mercenary.heading())
                .update();
    }

    public void delete(int npcId, int x, int y, int z) {
        jdbcClient.sql(DELETE_AT).param(npcId).param(x).param(y).param(z).update();
    }

    public void deleteByCastle(int castleId) {
        jdbcClient.sql(DELETE_BY_CASTLE).param(castleId).update();
    }
}
