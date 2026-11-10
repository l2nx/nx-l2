/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import static app.l2nx.highfive.repository.LegacyValues.textOrNull;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterQuestVariable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Quest variables of a character. The variable {@code <state>} holds the state of the quest itself; an empty value is
 * stored as {@code NULL} and read back as {@code null}.
 */
@Repository
@RequiredArgsConstructor
public class CharacterQuestRepository {
    private static final String SELECT_STATES = """
        SELECT quest_name, name, content
        FROM character_quests
        WHERE character_id = ? AND name = '<state>'
        ORDER BY quest_name\
        """;
    private static final String SELECT_STATES_BY_CONTENT = """
        SELECT quest_name, name, content
        FROM character_quests
        WHERE character_id = ? AND name = '<state>' AND content = ?
        ORDER BY quest_name\
        """;
    private static final String SELECT_VARIABLES = """
        SELECT quest_name, name, content
        FROM character_quests
        WHERE character_id = ? AND name <> '<state>'\
        """;
    private static final String SELECT_QUEST_VARIABLES =
            "SELECT quest_name, name, content FROM character_quests WHERE character_id = ? AND quest_name = ?";
    private static final String UPSERT_VARIABLE = """
        INSERT INTO character_quests (character_id, quest_name, name, content)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (character_id, quest_name, name) DO UPDATE SET content = EXCLUDED.content\
        """;
    private static final String UPDATE_VARIABLE =
            "UPDATE character_quests SET content = ? WHERE character_id = ? AND quest_name = ? AND name = ?";
    private static final String DELETE_VARIABLE =
            "DELETE FROM character_quests WHERE character_id = ? AND quest_name = ? AND name = ?";
    private static final String DELETE_QUEST = "DELETE FROM character_quests WHERE character_id = ? AND quest_name = ?";
    private static final String DELETE_QUEST_KEEPING_STATE =
            "DELETE FROM character_quests WHERE character_id = ? AND quest_name = ? AND name <> '<state>'";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CharacterQuestVariable> findStates(UUID characterId) {
        return jdbcClient
                .sql(SELECT_STATES)
                .param(characterId)
                .query(this::mapVariable)
                .list();
    }

    public List<CharacterQuestVariable> findStates(int objectId) {
        return registry.findId(objectId).map(this::findStates).orElse(List.of());
    }

    public List<CharacterQuestVariable> findStates(int objectId, String state) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_STATES_BY_CONTENT)
                        .param(id)
                        .param(state)
                        .query(this::mapVariable)
                        .list())
                .orElse(List.of());
    }

    /**
     * @return the variables of every quest of the character except the states
     */
    public List<CharacterQuestVariable> findVariables(UUID characterId) {
        return jdbcClient
                .sql(SELECT_VARIABLES)
                .param(characterId)
                .query(this::mapVariable)
                .list();
    }

    public List<CharacterQuestVariable> findVariables(int objectId) {
        return registry.findId(objectId).map(this::findVariables).orElse(List.of());
    }

    /**
     * @return every variable of the quest, its state included
     */
    public List<CharacterQuestVariable> findVariables(int objectId, String questName) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_QUEST_VARIABLES)
                        .param(id)
                        .param(questName)
                        .query(this::mapVariable)
                        .list())
                .orElse(List.of());
    }

    public void save(int objectId, String questName, String name, String content) {
        jdbcClient
                .sql(UPSERT_VARIABLE)
                .param(References.required(registry, objectId))
                .param(questName)
                .param(name)
                .param(textOrNull(content))
                .update();
    }

    public void update(int objectId, String questName, String name, String content) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_VARIABLE)
                        .param(textOrNull(content))
                        .param(id)
                        .param(questName)
                        .param(name)
                        .update());
    }

    public void deleteVariable(int objectId, String questName, String name) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_VARIABLE)
                        .param(id)
                        .param(questName)
                        .param(name)
                        .update());
    }

    public void deleteQuest(int objectId, String questName) {
        registry.findId(objectId)
                .ifPresent(id ->
                        jdbcClient.sql(DELETE_QUEST).param(id).param(questName).update());
    }

    public void deleteQuestKeepingState(int objectId, String questName) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_QUEST_KEEPING_STATE)
                        .param(id)
                        .param(questName)
                        .update());
    }

    private CharacterQuestVariable mapVariable(ResultSet rs, int rowNum) throws SQLException {
        return CharacterQuestVariable.builder()
                .questName(rs.getString("quest_name"))
                .name(rs.getString("name"))
                .content(rs.getString("content"))
                .build();
    }
}
