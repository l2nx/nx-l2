/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterRebirthRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterRebirthRepositoryTest extends RepositoryTest {
    private final CharacterRebirthRepository repository = new CharacterRebirthRepository(JDBC, registry);

    @Nested
    class FindRebirthCount {
        @Test
        void findRebirthCount_shouldReturnStoredCount() {
            final UUID character = insertCharacter();
            insertRebirth(character, 3, "{}");

            assertThat(repository.findRebirthCount(registry.handleOf(character)))
                    .isEqualTo(3);
        }

        @Test
        void findRebirthCount_shouldReturnZero_whenNeverReborn() {
            assertThat(repository.findRebirthCount(registry.handleOf(insertCharacter())))
                    .isZero();
        }
    }

    @Nested
    class SaveRebirthCount {
        @Test
        void saveRebirthCount_shouldInsertRow_whenFirstRebirth() {
            final UUID character = insertCharacter();

            repository.saveRebirthCount(registry.handleOf(character), 1);

            assertThat(rebirth(character)).contains(tuple(1, "{}"));
        }

        @Test
        void saveRebirthCount_shouldUpdateCount_andKeepSelectedSkills() {
            final UUID character = insertCharacter();
            insertRebirth(character, 1, "{100,200}");

            repository.saveRebirthCount(registry.handleOf(character), 2);

            assertThat(rebirth(character)).contains(tuple(2, "{100,200}"));
        }
    }

    @Nested
    class FindSelectedSkillIds {
        @Test
        void findSelectedSkillIds_shouldReturnStoredSkills() {
            final UUID character = insertCharacter();
            insertRebirth(character, 1, "{100,200}");

            assertThat(repository.findSelectedSkillIds(registry.handleOf(character)))
                    .containsExactly(100, 200);
        }

        @Test
        void findSelectedSkillIds_shouldBeEmpty_whenNeverReborn() {
            assertThat(repository.findSelectedSkillIds(registry.handleOf(insertCharacter())))
                    .isEmpty();
        }
    }

    @Nested
    class SaveSelectedSkillIds {
        @Test
        void saveSelectedSkillIds_shouldReplaceSkills() {
            final UUID character = insertCharacter();
            insertRebirth(character, 1, "{100}");

            repository.saveSelectedSkillIds(registry.handleOf(character), List.of(300, 400));

            assertThat(rebirth(character)).contains(tuple(1, "{300,400}"));
        }

        @Test
        void saveSelectedSkillIds_shouldWriteNothing_whenNeverReborn() {
            final UUID character = insertCharacter();

            repository.saveSelectedSkillIds(registry.handleOf(character), List.of(300));

            assertThat(rebirth(character)).isEmpty();
        }
    }

    private static void insertRebirth(UUID character, int count, String skillIds) {
        JDBC.sql("""
            INSERT INTO character_rebirths (character_id, rebirth_count, selected_skill_ids)
            VALUES (?, ?, ?::integer[])\
            """).param(character).param(count).param(skillIds).update();
    }

    private static Optional<Tuple> rebirth(UUID character) {
        return JDBC.sql("""
            SELECT rebirth_count, selected_skill_ids::text AS skill_ids
            FROM character_rebirths
            WHERE character_id = ?\
            """)
                .param(character)
                .query((rs, rowNum) -> tuple(rs.getInt("rebirth_count"), rs.getString("skill_ids")))
                .optional();
    }
}
