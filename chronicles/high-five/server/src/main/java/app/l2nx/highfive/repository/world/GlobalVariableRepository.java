/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class GlobalVariableRepository {
    private static final String SELECT_ALL = "SELECT name, content FROM global_variables ORDER BY name";
    private static final String DELETE_ALL = "DELETE FROM global_variables";
    private static final String INSERT = "INSERT INTO global_variables (name, content) VALUES (?, ?)";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public Map<String, String> findAll() {
        final Map<String, String> variables = new LinkedHashMap<>();
        jdbcClient.sql(SELECT_ALL).query(rs -> {
            variables.put(rs.getString("name"), rs.getString("content"));
        });
        return variables;
    }

    /**
     * Replaces every stored variable with the given ones, each stored as {@link String#valueOf}; a variable whose value
     * is {@code null} or empty is not stored.
     */
    @Transactional
    public void replaceAll(Map<String, ?> variables) {
        jdbcClient.sql(DELETE_ALL).update();
        final List<Object[]> rows = new ArrayList<>();
        variables.forEach((name, value) -> {
            final String content = value == null ? null : String.valueOf(value);
            if ((content != null) && !content.isEmpty()) {
                rows.add(new Object[] {name, content});
            }
        });
        jdbcTemplate.batchUpdate(INSERT, rows);
    }

    public void deleteAll() {
        jdbcClient.sql(DELETE_ALL).update();
    }
}
