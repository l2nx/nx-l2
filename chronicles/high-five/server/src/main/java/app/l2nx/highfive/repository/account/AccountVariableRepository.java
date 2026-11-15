/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.account;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account-wide game variables ({@code AccountVariables}, the secondary password). A {@code null} content is a variable
 * stored with an empty value.
 */
@Repository
@RequiredArgsConstructor
public class AccountVariableRepository {
    private static final String SELECT_ALL_WITHOUT_PREFIX =
            "SELECT name, content FROM account_variables WHERE account_id = ? AND NOT starts_with(name, ?)";
    private static final String SELECT_BY_NAME_PREFIX =
            "SELECT name, content FROM account_variables WHERE account_id = ? AND starts_with(name, ?)";
    private static final String INSERT = "INSERT INTO account_variables (account_id, name, content) VALUES (?, ?, ?)";
    private static final String UPDATE = "UPDATE account_variables SET content = ? WHERE account_id = ? AND name = ?";
    private static final String UPSERT = """
        INSERT INTO account_variables (account_id, name, content)
        VALUES (?, ?, ?)
        ON CONFLICT (account_id, name) DO UPDATE SET content = EXCLUDED.content\
        """;
    private static final String DELETE = "DELETE FROM account_variables WHERE account_id = ? AND name = ?";
    private static final String DELETE_BY_NAME = "DELETE FROM account_variables WHERE name = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    /**
     * @return the variable values by name, without the names starting with {@code excludedPrefix}; a value stored empty
     *     is {@code null}
     */
    public Map<String, String> findAllWithoutPrefix(UUID accountId, String excludedPrefix) {
        return toMap(jdbcClient.sql(SELECT_ALL_WITHOUT_PREFIX).param(accountId).param(excludedPrefix));
    }

    public Map<String, String> findByNamePrefix(UUID accountId, String namePrefix) {
        return toMap(jdbcClient.sql(SELECT_BY_NAME_PREFIX).param(accountId).param(namePrefix));
    }

    /**
     * Fails with a duplicate key error when the variable exists.
     */
    public void insert(UUID accountId, String name, String content) {
        jdbcClient
                .sql(INSERT)
                .param(accountId)
                .param(name)
                .param(StringUtils.defaultIfEmpty(content, null))
                .update();
    }

    public boolean update(UUID accountId, String name, String content) {
        return jdbcClient
                        .sql(UPDATE)
                        .param(StringUtils.defaultIfEmpty(content, null))
                        .param(accountId)
                        .param(name)
                        .update()
                > 0;
    }

    public void upsert(UUID accountId, String name, String content) {
        jdbcClient
                .sql(UPSERT)
                .param(accountId)
                .param(name)
                .param(StringUtils.defaultIfEmpty(content, null))
                .update();
    }

    /**
     * Applies the changes tracked since the last save: the deleted names first, then the added and modified values.
     */
    @Transactional
    public void saveChanges(UUID accountId, Collection<String> deletedNames, Map<String, String> changedValues) {
        if (!deletedNames.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    DELETE,
                    deletedNames.stream()
                            .map(name -> new Object[] {accountId, name})
                            .toList());
        }
        if (!changedValues.isEmpty()) {
            final List<Object[]> rows = changedValues.entrySet().stream()
                    .map(entry ->
                            new Object[] {accountId, entry.getKey(), StringUtils.defaultIfEmpty(entry.getValue(), null)
                            })
                    .toList();
            jdbcTemplate.batchUpdate(UPSERT, rows);
        }
    }

    public void deleteByName(String name) {
        jdbcClient.sql(DELETE_BY_NAME).param(name).update();
    }

    private static Map<String, String> toMap(JdbcClient.StatementSpec statement) {
        final Map<String, String> values = new LinkedHashMap<>();
        statement.query(rs -> {
            values.put(rs.getString("name"), rs.getString("content"));
        });
        return values;
    }
}
