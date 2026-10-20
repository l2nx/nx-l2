/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CastleFunctionRepository {
    private static final String SELECT_BY_CASTLE = """
        SELECT function_type, function_level, lease, rate_ms, ends_at
        FROM castle_functions
        WHERE castle_id = ?
        ORDER BY function_type\
        """;
    private static final String UPSERT = """
        INSERT INTO castle_functions (castle_id, function_type, function_level, lease, rate_ms, ends_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (castle_id, function_type) DO UPDATE
        SET function_level = EXCLUDED.function_level, lease = EXCLUDED.lease, rate_ms = EXCLUDED.rate_ms,
            ends_at = EXCLUDED.ends_at\
        """;
    private static final String DELETE = "DELETE FROM castle_functions WHERE castle_id = ? AND function_type = ?";

    private final JdbcClient jdbcClient;

    public List<ResidenceFunction> findByCastle(int castleId) {
        return jdbcClient
                .sql(SELECT_BY_CASTLE)
                .param(castleId)
                .query(ResidenceFunctionRows.MAPPER)
                .list();
    }

    public void save(int castleId, ResidenceFunction function) {
        ResidenceFunctionRows.upsert(jdbcClient, UPSERT, castleId, function);
    }

    public void delete(int castleId, int functionType) {
        jdbcClient.sql(DELETE).param(castleId).param(functionType).update();
    }
}
