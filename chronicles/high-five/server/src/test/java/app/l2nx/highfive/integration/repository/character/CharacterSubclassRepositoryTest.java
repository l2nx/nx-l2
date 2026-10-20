/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static app.l2nx.highfive.integration.repository.character.CharacterHennaRepositoryTest.henna;
import static app.l2nx.highfive.integration.repository.character.CharacterShortcutRepositoryTest.shortcut;
import static app.l2nx.highfive.integration.repository.character.CharacterSkillRepositoryTest.skill;
import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterHennaRepository;
import app.l2nx.highfive.repository.character.CharacterShortcutRepository;
import app.l2nx.highfive.repository.character.CharacterSkillRepository;
import app.l2nx.highfive.repository.character.CharacterSkillSaveRepository;
import app.l2nx.highfive.repository.character.CharacterSubclassRepository;
import app.l2nx.highfive.repository.character.model.CharacterSkillSave;
import app.l2nx.highfive.repository.character.model.CharacterSubclass;
import app.l2nx.highfive.repository.character.model.SkillRestoreType;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("character_skill_saves")
class CharacterSubclassRepositoryTest extends RepositoryTest {
    private static final CharacterSubclass FIRST = subclass(1, 2, 835863, 1000, 40);
    private static final CharacterSubclass SECOND = subclass(2, 3, 0, 0, 40);

    private final CharacterSkillRepository skills = new CharacterSkillRepository(JDBC, JDBC_TEMPLATE, registry);
    private final CharacterSkillSaveRepository skillSaves =
            new CharacterSkillSaveRepository(JDBC, JDBC_TEMPLATE, registry);
    private final CharacterShortcutRepository shortcuts = new CharacterShortcutRepository(JDBC, registry);
    private final CharacterHennaRepository hennas = new CharacterHennaRepository(JDBC, registry);
    private final CharacterSubclassRepository repository =
            new CharacterSubclassRepository(JDBC, JDBC_TEMPLATE, registry, skills, skillSaves, shortcuts, hennas);

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreSubclass() {
            final int objectId = registry.handleOf(insertCharacter());

            repository.insert(objectId, FIRST);

            assertThat(repository.findAll(objectId)).containsExactly(FIRST);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldOrderByClassIndex() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, SECOND);
            repository.insert(objectId, FIRST);

            assertThat(repository.findAll(objectId)).containsExactly(FIRST, SECOND);
        }
    }

    @Nested
    class FindByClassId {
        @Test
        void findByClassId_shouldReturnTheSubclassOfTheClass() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, FIRST);
            repository.insert(objectId, SECOND);

            assertThat(repository.findByClassId(objectId, SECOND.classId()))
                    .get()
                    .isEqualTo(SECOND);
        }

        @Test
        void findByClassId_shouldBeEmpty_whenClassNotTaken() {
            final int objectId = registry.handleOf(insertCharacter());

            assertThat(repository.findByClassId(objectId, 88)).isEmpty();
        }
    }

    @Nested
    class UpdateAll {
        @Test
        void updateAll_shouldWriteEverySubclassByClassIndex() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, FIRST);
            repository.insert(objectId, SECOND);
            final CharacterSubclass first =
                    FIRST.toBuilder().classId(5).exp(900000).sp(20).level(45).build();
            final CharacterSubclass second = SECOND.toBuilder().level(41).build();

            repository.updateAll(objectId, List.of(first, second));

            assertThat(repository.findAll(objectId)).containsExactly(first, second);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveSubclassWithTheRowsOfItsClassIndex() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, FIRST);
            repository.insert(objectId, SECOND);
            for (int classIndex : new int[] {1, 2}) {
                skills.save(objectId, classIndex, skill(3, 1));
                hennas.insert(objectId, classIndex, henna(1, 180));
                shortcuts.save(objectId, classIndex, shortcut(0, 0, ShortcutType.ACTION, 2, 0));
                final CharacterSkillSave reuse = CharacterSkillSave.builder()
                        .skillId(3)
                        .skillLevel(1)
                        .restoreType(SkillRestoreType.REUSE)
                        .reuseEndsAt(NOW)
                        .build();
                TRANSACTIONS.executeWithoutResult(
                        status -> skillSaves.replaceAll(objectId, classIndex, List.of(reuse)));
            }

            TRANSACTIONS.executeWithoutResult(status -> repository.delete(objectId, 1));

            assertThat(repository.findAll(objectId)).containsExactly(SECOND);
            assertThat(skills.findAll(objectId, 1)).isEmpty();
            assertThat(hennas.findAll(objectId, 1)).isEmpty();
            assertThat(shortcuts.findAll(objectId, 1)).isEmpty();
            assertThat(skillSaves.findAll(objectId, 1)).isEmpty();
            assertThat(skills.findAll(objectId, 2)).hasSize(1);
            assertThat(hennas.findAll(objectId, 2)).hasSize(1);
            assertThat(shortcuts.findAll(objectId, 2)).hasSize(1);
            assertThat(skillSaves.findAll(objectId, 2)).hasSize(1);
        }
    }

    private static CharacterSubclass subclass(int classIndex, int classId, long exp, long sp, int level) {
        return CharacterSubclass.builder()
                .classIndex(classIndex)
                .classId(classId)
                .exp(exp)
                .sp(sp)
                .level(level)
                .build();
    }
}
