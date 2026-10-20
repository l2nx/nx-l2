/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterVariableRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterVariableRepositoryTest extends RepositoryTest {
    private final CharacterVariableRepository repository =
            new CharacterVariableRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class Save {
        @Test
        void save_shouldInsertThenReplaceContent() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, "UNCLAIMED_OLYMPIAD_POINTS", "3");

            repository.save(objectId, "UNCLAIMED_OLYMPIAD_POINTS", "5");

            assertThat(repository.findAll(objectId)).containsExactly(entry("UNCLAIMED_OLYMPIAD_POINTS", "5"));
        }

        @Test
        void save_shouldStoreNull_whenContentEmpty() {
            final int objectId = registry.handleOf(insertCharacter());

            repository.save(objectId, "a", "");

            assertThat(repository.findAll(objectId)).containsExactly(entry("a", null));
        }
    }

    @Nested
    class SaveChanges {
        @Test
        void save_shouldDeleteRemovedAndUpsertChangedVariables() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, "a", "1");
            repository.save(objectId, "b", "2");

            TRANSACTIONS.executeWithoutResult(
                    status -> repository.save(objectId, List.of("a"), Map.of("b", "3", "c", "4")));

            assertThat(repository.findAll(objectId)).containsExactly(entry("b", "3"), entry("c", "4"));
        }

        @Test
        void save_shouldWriteNothing_whenNoChanges() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, "a", "1");

            TRANSACTIONS.executeWithoutResult(status -> repository.save(objectId, List.of(), Map.of()));

            assertThat(repository.findAll(objectId)).containsExactly(entry("a", "1"));
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldBeEmpty_whenNoVariables() {
            assertThat(repository.findAll(registry.handleOf(insertCharacter()))).isEmpty();
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveEveryVariableOfTheCharacter() {
            final int objectId = registry.handleOf(insertCharacter());
            final int other = registry.handleOf(insertCharacter());
            repository.save(objectId, "a", "1");
            repository.save(other, "a", "1");

            repository.deleteAll(objectId);

            assertThat(repository.findAll(objectId)).isEmpty();
            assertThat(repository.findAll(other)).hasSize(1);
        }
    }
}
