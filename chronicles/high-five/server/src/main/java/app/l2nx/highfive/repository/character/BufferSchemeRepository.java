/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.character.model.BufferScheme;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class BufferSchemeRepository {
    private static final String SELECT_ALL = "SELECT character_id, name, skill_ids FROM buffer_schemes";
    private static final String DELETE_ALL = "DELETE FROM buffer_schemes";
    private static final String INSERT = "INSERT INTO buffer_schemes (character_id, name, skill_ids) VALUES (?, ?, ?)";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<BufferScheme> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> BufferScheme.builder()
                        .characterId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                        .name(rs.getString("name"))
                        .skillIds(skillIds(rs))
                        .build())
                .list();
    }

    @Transactional
    public void replaceAll(Collection<BufferScheme> schemes) {
        jdbcClient.sql(DELETE_ALL).update();
        if (schemes.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT,
                schemes.stream()
                        // A character deleted since it saved the scheme has no row to reference.
                        .flatMap(scheme -> registry.findId(scheme.characterId()).stream()
                                .map(id -> new Object[] {
                                    id, scheme.name(), scheme.skillIds().toArray(Integer[]::new)
                                }))
                        .toList());
    }

    private static List<Integer> skillIds(ResultSet rs) throws SQLException {
        final Array array = rs.getArray("skill_ids");
        return List.of((Integer[]) array.getArray());
    }
}
