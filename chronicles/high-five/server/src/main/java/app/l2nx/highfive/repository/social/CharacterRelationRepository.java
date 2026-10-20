/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.social;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.social.model.RelationType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CharacterRelationRepository {
    private static final String SELECT_TARGETS =
            "SELECT target_character_id FROM character_relations WHERE character_id = ? AND relation = ?";
    private static final String INSERT = """
        INSERT INTO character_relations (character_id, target_character_id, relation)
        VALUES (?, ?, ?)
        ON CONFLICT (character_id, target_character_id, relation) DO NOTHING\
        """;
    private static final String DELETE = """
        DELETE FROM character_relations
        WHERE character_id = ? AND target_character_id = ? AND relation = ?\
        """;
    private static final String INSERT_FRIENDSHIP = """
        INSERT INTO character_relations (character_id, target_character_id, relation)
        VALUES (?, ?, 'FRIEND'), (?, ?, 'FRIEND')
        ON CONFLICT (character_id, target_character_id, relation) DO NOTHING\
        """;
    private static final String DELETE_FRIENDSHIP = """
        DELETE FROM character_relations
        WHERE relation = 'FRIEND'
          AND ((character_id = ? AND target_character_id = ?) OR (character_id = ? AND target_character_id = ?))\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<Integer> findTargetIds(int characterId, RelationType relation) {
        return registry.findId(characterId)
                .map(id -> jdbcClient
                        .sql(SELECT_TARGETS)
                        .param(id)
                        .param(relation.name())
                        .query((rs, rowNum) -> registry.handleOf(rs.getObject("target_character_id", UUID.class)))
                        .list())
                .orElse(List.of());
    }

    public void add(int characterId, int targetId, RelationType relation) {
        jdbcClient
                .sql(INSERT)
                .param(References.required(registry, characterId))
                .param(References.required(registry, targetId))
                .param(relation.name())
                .update();
    }

    public void remove(int characterId, int targetId, RelationType relation) {
        registry.findId(characterId)
                .ifPresent(id -> registry.findId(targetId)
                        .ifPresent(target -> jdbcClient
                                .sql(DELETE)
                                .param(id)
                                .param(target)
                                .param(relation.name())
                                .update()));
    }

    public void addFriendship(int characterId, int friendId) {
        final UUID character = References.required(registry, characterId);
        final UUID friend = References.required(registry, friendId);
        jdbcClient
                .sql(INSERT_FRIENDSHIP)
                .param(character)
                .param(friend)
                .param(friend)
                .param(character)
                .update();
    }

    /**
     * Blocks stay.
     */
    public void removeFriendship(int characterId, int friendId) {
        registry.findId(characterId)
                .ifPresent(character -> registry.findId(friendId)
                        .ifPresent(friend -> jdbcClient
                                .sql(DELETE_FRIENDSHIP)
                                .param(character)
                                .param(friend)
                                .param(friend)
                                .param(character)
                                .update()));
    }
}
