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
public class FortFunctionRepository {
    private static final String SELECT_BY_FORT = """
        SELECT function_type, function_level, lease, rate_ms, ends_at
        FROM fort_functions
        WHERE fort_id = ?
        ORDER BY function_type\
        """;
    private static final String UPSERT = """
        INSERT INTO fort_functions (fort_id, function_type, function_level, lease, rate_ms, ends_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (fort_id, function_type) DO UPDATE
        SET function_level = EXCLUDED.function_level, lease = EXCLUDED.lease, rate_ms = EXCLUDED.rate_ms,
            ends_at = EXCLUDED.ends_at\
        """;
    private static final String DELETE = "DELETE FROM fort_functions WHERE fort_id = ? AND function_type = ?";

    private final JdbcClient jdbcClient;

    public List<ResidenceFunction> findByFort(int fortId) {
        return jdbcClient
                .sql(SELECT_BY_FORT)
                .param(fortId)
                .query(ResidenceFunctionRows.MAPPER)
                .list();
    }

    public void save(int fortId, ResidenceFunction function) {
        ResidenceFunctionRows.upsert(jdbcClient, UPSERT, fortId, function);
    }

    public void delete(int fortId, int functionType) {
        jdbcClient.sql(DELETE).param(fortId).param(functionType).update();
    }
}
