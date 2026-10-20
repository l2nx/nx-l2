/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterSubclass;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * A subclass row and, on removal, the per-class rows that belong to its class index.
 */
@Repository
@RequiredArgsConstructor
public class CharacterSubclassRepository {
    private static final String SELECT_SUBCLASSES = """
        SELECT class_index, class_id, exp, sp, class_level
        FROM character_subclasses
        WHERE character_id = ?
        ORDER BY class_index\
        """;
    private static final String SELECT_BY_CLASS_ID = """
        SELECT class_index, class_id, exp, sp, class_level
        FROM character_subclasses
        WHERE character_id = ? AND class_id = ?\
        """;
    private static final String INSERT_SUBCLASS = """
        INSERT INTO character_subclasses (character_id, class_index, class_id, exp, sp, class_level)
        VALUES (?, ?, ?, ?, ?, ?)\
        """;
    private static final String UPDATE_SUBCLASS = """
        UPDATE character_subclasses SET class_id = ?, exp = ?, sp = ?, class_level = ?
        WHERE character_id = ? AND class_index = ?\
        """;
    private static final String DELETE_SUBCLASS =
            "DELETE FROM character_subclasses WHERE character_id = ? AND class_index = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;
    private final CharacterSkillRepository skillRepository;
    private final CharacterSkillSaveRepository skillSaveRepository;
    private final CharacterShortcutRepository shortcutRepository;
    private final CharacterHennaRepository hennaRepository;

    public List<CharacterSubclass> findAll(int objectId) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_SUBCLASSES)
                        .param(id)
                        .query(this::mapSubclass)
                        .list())
                .orElse(List.of());
    }

    public Optional<CharacterSubclass> findByClassId(int objectId, int classId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_BY_CLASS_ID)
                        .param(id)
                        .param(classId)
                        .query(this::mapSubclass)
                        .optional());
    }

    public void insert(int objectId, CharacterSubclass subclass) {
        jdbcClient
                .sql(INSERT_SUBCLASS)
                .param(References.required(registry, objectId))
                .param(subclass.classIndex())
                .param(subclass.classId())
                .param(subclass.exp())
                .param(subclass.sp())
                .param(subclass.level())
                .update();
    }

    public void updateAll(int objectId, List<CharacterSubclass> subclasses) {
        final UUID id = References.required(registry, objectId);
        jdbcTemplate.batchUpdate(
                UPDATE_SUBCLASS,
                subclasses.stream()
                        .map(subclass -> new Object[] {
                            subclass.classId(),
                            subclass.exp(),
                            subclass.sp(),
                            subclass.level(),
                            id,
                            subclass.classIndex()
                        })
                        .toList());
    }

    /**
     * Removes the subclass with the hennas, shortcuts, stored effects and skills of its class index.
     */
    @Transactional
    public void delete(int objectId, int classIndex) {
        hennaRepository.deleteAll(objectId, classIndex);
        shortcutRepository.deleteAll(objectId, classIndex);
        skillSaveRepository.deleteAll(objectId, classIndex);
        skillRepository.deleteAll(objectId, classIndex);
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_SUBCLASS)
                        .param(id)
                        .param(classIndex)
                        .update());
    }

    private CharacterSubclass mapSubclass(ResultSet rs, int rowNum) throws SQLException {
        return CharacterSubclass.builder()
                .classIndex(rs.getInt("class_index"))
                .classId(rs.getInt("class_id"))
                .exp(rs.getLong("exp"))
                .sp(rs.getLong("sp"))
                .level(rs.getInt("class_level"))
                .build();
    }
}
