/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.BufferSchemeRepository;
import app.l2nx.highfive.repository.character.model.BufferScheme;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

// The repository reads and rewrites the whole table, so its tests must not interleave.
@ResourceLock("buffer_schemes")
class BufferSchemeRepositoryTest extends RepositoryTest {
    private final BufferSchemeRepository repository = new BufferSchemeRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnHandlesAndSkillLists() {
            JDBC.sql("DELETE FROM buffer_schemes").update();
            final UUID character = insertCharacter();
            insertScheme(character, "Mage", "{1204,1040}");
            insertScheme(character, "none", "{}");

            final List<BufferScheme> actual = repository.findAll();

            final int handle = registry.handleOf(character);
            assertThat(actual)
                    .containsExactlyInAnyOrder(
                            new BufferScheme(handle, "Mage", List.of(1204, 1040)),
                            new BufferScheme(handle, "none", List.of()));
        }
    }

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldReplaceEveryStoredScheme() {
            insertScheme(insertCharacter(), "old", "{1}");
            final UUID character = insertCharacter();
            final int handle = registry.handleOf(character);
            final List<BufferScheme> schemes = List.of(
                    new BufferScheme(handle, "Fighter", List.of(1068, 1040)),
                    new BufferScheme(handle, "empty", List.of()));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(schemes));

            assertThat(schemes())
                    .containsExactlyInAnyOrder(
                            tuple(character, "Fighter", "{1068,1040}"), tuple(character, "empty", "{}"));
        }

        @Test
        void replaceAll_shouldClearTable_whenNoSchemes() {
            insertScheme(insertCharacter(), "old", "{1}");

            repository.replaceAll(List.of());

            assertThat(schemes()).isEmpty();
        }
    }

    private static void insertScheme(UUID character, String name, String skillIds) {
        JDBC.sql("INSERT INTO buffer_schemes (character_id, name, skill_ids) VALUES (?, ?, ?::integer[])")
                .param(character)
                .param(name)
                .param(skillIds)
                .update();
    }

    private static List<Tuple> schemes() {
        return JDBC.sql("SELECT character_id, name, skill_ids::text AS skill_ids FROM buffer_schemes")
                .query((rs, rowNum) -> tuple(
                        rs.getObject("character_id", UUID.class), rs.getString("name"), rs.getString("skill_ids")))
                .list();
    }
}
