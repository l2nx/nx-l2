/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

final class ResidenceFunctionRows {
    static final RowMapper<ResidenceFunction> MAPPER = (rs, rowNum) -> ResidenceFunction.builder()
            .type(rs.getInt("function_type"))
            .level(rs.getInt("function_level"))
            .lease(rs.getInt("lease"))
            .rate(rs.getLong("rate_ms"))
            .endTime(JdbcTimes.instant(rs, "ends_at"))
            .build();

    private ResidenceFunctionRows() {}

    static void upsert(JdbcClient jdbcClient, String sql, int residenceId, ResidenceFunction function) {
        jdbcClient
                .sql(sql)
                .param(residenceId)
                .param(function.type())
                .param(function.level())
                .param(function.lease())
                .param(function.rate())
                .param(JdbcTimes.utc(function.endTime()))
                .update();
    }
}
