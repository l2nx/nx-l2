/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterSkillRepository;
import app.l2nx.highfive.repository.character.model.CharacterSkill;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterSkillRepositoryTest extends RepositoryTest {
    private final CharacterSkillRepository repository = new CharacterSkillRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnSkillsOfTheClassIndexOnly() {
            final UUID id = insertCharacter();
            insertSkill(id, 0, 3, 2);
            insertSkill(id, 1, 4, 1);

            final List<CharacterSkill> actual = repository.findAll(registry.handleOf(id), 0);

            assertThat(actual).containsExactly(skill(3, 2));
        }
    }

    @Nested
    class UpdateLevel {
        @Test
        void updateLevel_shouldChangeLevelOfTheSkill() {
            final UUID id = insertCharacter();
            insertSkill(id, 0, 3, 2);

            repository.updateLevel(registry.handleOf(id), 0, 3, 5);

            assertThat(repository.findAll(registry.handleOf(id), 0)).containsExactly(skill(3, 5));
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldInsertSkill_whenNew() {
            final UUID id = insertCharacter();

            repository.save(registry.handleOf(id), 1, skill(3, 2));

            assertThat(repository.findAll(registry.handleOf(id), 1)).containsExactly(skill(3, 2));
        }

        @Test
        void save_shouldReplaceLevel_whenSkillStored() {
            final UUID id = insertCharacter();
            insertSkill(id, 0, 3, 2);

            repository.save(registry.handleOf(id), 0, skill(3, 7));

            assertThat(repository.findAll(registry.handleOf(id), 0)).containsExactly(skill(3, 7));
        }
    }

    @Nested
    class SaveAll {
        @Test
        void saveAll_shouldUpsertEverySkill() {
            final UUID id = insertCharacter();
            insertSkill(id, 0, 3, 2);

            repository.saveAll(registry.handleOf(id), 0, List.of(skill(3, 4), skill(5, 1)));

            assertThat(repository.findAll(registry.handleOf(id), 0))
                    .containsExactlyInAnyOrder(skill(3, 4), skill(5, 1));
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyTheSkill() {
            final UUID id = insertCharacter();
            insertSkill(id, 0, 3, 2);
            insertSkill(id, 0, 5, 1);

            repository.delete(registry.handleOf(id), 0, 3);

            assertThat(repository.findAll(registry.handleOf(id), 0)).containsExactly(skill(5, 1));
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveSkillsOfTheClassIndexOnly() {
            final UUID id = insertCharacter();
            insertSkill(id, 0, 3, 2);
            insertSkill(id, 1, 4, 1);

            repository.deleteAll(registry.handleOf(id), 1);

            assertThat(repository.findAll(registry.handleOf(id), 1)).isEmpty();
            assertThat(repository.findAll(registry.handleOf(id), 0)).hasSize(1);
        }
    }

    static void insertSkill(UUID characterId, int classIndex, int skillId, int skillLevel) {
        JDBC.sql("INSERT INTO character_skills (character_id, class_index, skill_id, skill_level) VALUES (?, ?, ?, ?)")
                .param(characterId)
                .param(classIndex)
                .param(skillId)
                .param(skillLevel)
                .update();
    }

    static CharacterSkill skill(int skillId, int skillLevel) {
        return CharacterSkill.builder().skillId(skillId).skillLevel(skillLevel).build();
    }
}
