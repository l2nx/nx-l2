/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.world.model.CoupleRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CoupleRepository {
    private static final String COLUMNS = "id, first_character_id, second_character_id, engaged_at, married_at";
    private static final String SELECT_ALL = "SELECT " + COLUMNS + " FROM couples ORDER BY id";
    private static final String SELECT_BY_ID = "SELECT " + COLUMNS + " FROM couples WHERE id = ?";
    private static final String INSERT = """
        INSERT INTO couples (first_character_id, second_character_id, engaged_at)
        VALUES (?, ?, ?)
        RETURNING id\
        """;
    private static final String UPDATE_MARRIED = "UPDATE couples SET married_at = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM couples WHERE id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CoupleRow> findAll() {
        return jdbcClient.sql(SELECT_ALL).query((rs, rowNum) -> couple(rs)).list();
    }

    public Optional<CoupleRow> findById(long id) {
        return jdbcClient
                .sql(SELECT_BY_ID)
                .param(id)
                .query((rs, rowNum) -> couple(rs))
                .optional();
    }

    public long insert(int firstObjectId, int secondObjectId, Instant engagedAt) {
        return jdbcClient
                .sql(INSERT)
                .param(References.required(registry, firstObjectId))
                .param(References.required(registry, secondObjectId))
                .param(JdbcTimes.utc(engagedAt))
                .query(Long.class)
                .single();
    }

    public void updateMarried(long id, Instant marriedAt) {
        jdbcClient.sql(UPDATE_MARRIED).param(JdbcTimes.utc(marriedAt)).param(id).update();
    }

    public void delete(long id) {
        jdbcClient.sql(DELETE).param(id).update();
    }

    private CoupleRow couple(ResultSet rs) throws SQLException {
        return CoupleRow.builder()
                .id(rs.getLong("id"))
                .firstObjectId(registry.handleOf(rs.getObject("first_character_id", UUID.class)))
                .secondObjectId(registry.handleOf(rs.getObject("second_character_id", UUID.class)))
                .engagedAt(JdbcTimes.instant(rs, "engaged_at"))
                .marriedAt(JdbcTimes.instant(rs, "married_at"))
                .build();
    }
}
