/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.social.CharacterRelationRepository;
import app.l2nx.highfive.repository.social.model.RelationType;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CharacterRelationRepositoryTest extends RepositoryTest {
    private final CharacterRelationRepository repository = new CharacterRelationRepository(JDBC, registry);

    @Nested
    class FindTargetIds {
        @ParameterizedTest(name = "{0}")
        @EnumSource(RelationType.class)
        void findTargetIds_shouldReturnTargetsOfThatRelation(RelationType relation) {
            final UUID character = insertCharacter();
            final UUID friend = insertCharacter();
            final UUID blocked = insertCharacter();
            insertRelation(character, friend, "FRIEND");
            insertRelation(character, blocked, "BLOCK");
            final UUID expected = relation == RelationType.FRIEND ? friend : blocked;

            final List<Integer> actual = repository.findTargetIds(registry.handleOf(character), relation);

            assertThat(actual).containsExactly(registry.handleOf(expected));
        }
    }

    @Nested
    class Add {
        @Test
        void add_shouldStoreBlock_whenTargetIsAFriend() {
            final UUID character = insertCharacter();
            final UUID target = insertCharacter();
            insertRelation(character, target, "FRIEND");

            repository.add(registry.handleOf(character), registry.handleOf(target), RelationType.BLOCK);

            assertThat(relations(character)).containsExactlyInAnyOrder(tuple(target, "FRIEND"), tuple(target, "BLOCK"));
        }

        @Test
        void add_shouldKeepOneRow_whenRelationExists() {
            final UUID character = insertCharacter();
            final UUID target = insertCharacter();
            insertRelation(character, target, "BLOCK");

            repository.add(registry.handleOf(character), registry.handleOf(target), RelationType.BLOCK);

            assertThat(relations(character)).containsExactly(tuple(target, "BLOCK"));
        }
    }

    @Nested
    class Remove {
        @Test
        void remove_shouldDeleteOnlyThatRelation() {
            final UUID character = insertCharacter();
            final UUID target = insertCharacter();
            insertRelation(character, target, "FRIEND");
            insertRelation(character, target, "BLOCK");

            repository.remove(registry.handleOf(character), registry.handleOf(target), RelationType.BLOCK);

            assertThat(relations(character)).containsExactly(tuple(target, "FRIEND"));
        }
    }

    @Nested
    class AddFriendship {
        @Test
        void addFriendship_shouldStoreBothDirections() {
            final UUID character = insertCharacter();
            final UUID friend = insertCharacter();

            repository.addFriendship(registry.handleOf(character), registry.handleOf(friend));

            assertThat(relations(character)).containsExactly(tuple(friend, "FRIEND"));
            assertThat(relations(friend)).containsExactly(tuple(character, "FRIEND"));
        }
    }

    @Nested
    class RemoveFriendship {
        @Test
        void removeFriendship_shouldDeleteBothDirections_andKeepBlocks() {
            final UUID character = insertCharacter();
            final UUID friend = insertCharacter();
            insertRelation(character, friend, "FRIEND");
            insertRelation(friend, character, "FRIEND");
            insertRelation(friend, character, "BLOCK");

            repository.removeFriendship(registry.handleOf(character), registry.handleOf(friend));

            assertThat(relations(character)).isEmpty();
            assertThat(relations(friend)).containsExactly(tuple(character, "BLOCK"));
        }
    }

    private static void insertRelation(UUID character, UUID target, String relation) {
        JDBC.sql("INSERT INTO character_relations (character_id, target_character_id, relation) VALUES (?, ?, ?)")
                .param(character)
                .param(target)
                .param(relation)
                .update();
    }

    private static List<Tuple> relations(UUID character) {
        return JDBC.sql("SELECT target_character_id, relation FROM character_relations WHERE character_id = ?")
                .param(character)
                .query((rs, rowNum) -> tuple(rs.getObject("target_character_id", UUID.class), rs.getString("relation")))
                .list();
    }
}
