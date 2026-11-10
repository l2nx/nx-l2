/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterShortcutRepository;
import app.l2nx.highfive.repository.character.model.CharacterShortcut;
import app.l2nx.highfive.repository.character.model.StoredShortcut;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CharacterShortcutRepositoryTest extends RepositoryTest {
    private final CharacterShortcutRepository repository = new CharacterShortcutRepository(JDBC, registry);

    @Nested
    class Save {
        @Test
        void save_shouldStoreItemInstance_whenItemShortcut() {
            final UUID id = insertCharacter();
            final UUID itemId = insertItem(id, 1060, 5);
            final CharacterShortcut shortcut = shortcut(3, 1, ShortcutType.ITEM, registry.handleOf(itemId), 0);

            repository.save(registry.handleOf(id), 0, shortcut);

            assertThat(row(id))
                    .containsEntry("shortcut_type", "ITEM")
                    .containsEntry("item_id", itemId)
                    .containsEntry("target_id", null)
                    .containsEntry("skill_level", null);
            assertThat(repository.findAll(registry.handleOf(id), 0)).containsExactly(shortcut);
        }

        @Test
        void save_shouldStoreSkillLevel_whenSkillShortcut() {
            final UUID id = insertCharacter();
            final CharacterShortcut shortcut = shortcut(0, 0, ShortcutType.SKILL, 1204, 2);

            repository.save(registry.handleOf(id), 0, shortcut);

            assertThat(row(id)).containsEntry("target_id", 1204).containsEntry("skill_level", 2);
            assertThat(repository.findAll(registry.handleOf(id), 0)).containsExactly(shortcut);
        }

        @ParameterizedTest(name = "{0} shortcut stores no level")
        @EnumSource(
                value = ShortcutType.class,
                names = {"NONE", "ACTION", "MACRO", "RECIPE", "BOOKMARK"})
        void save_shouldStoreNullLevel_whenNotSkill(ShortcutType type) {
            final UUID id = insertCharacter();

            repository.save(registry.handleOf(id), 0, shortcut(5, 2, type, 7, 1));

            assertThat(row(id)).containsEntry("target_id", 7).containsEntry("skill_level", null);
            assertThat(repository.findAll(registry.handleOf(id), 0)).containsExactly(shortcut(5, 2, type, 7, 0));
        }

        @Test
        void save_shouldReplaceShortcutInTheSlot() {
            final UUID id = insertCharacter();
            final UUID itemId = insertItem(id, 1060, 5);
            repository.save(registry.handleOf(id), 0, shortcut(3, 1, ShortcutType.ITEM, registry.handleOf(itemId), 0));
            final CharacterShortcut action = shortcut(3, 1, ShortcutType.ACTION, 2, 0);

            repository.save(registry.handleOf(id), 0, action);

            assertThat(repository.findAll(registry.handleOf(id), 0)).containsExactly(action);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnShortcutsOfTheClassIndexOnly() {
            final int objectId = registry.handleOf(insertCharacter());
            final CharacterShortcut action = shortcut(0, 0, ShortcutType.ACTION, 2, 0);
            repository.save(objectId, 0, action);
            repository.save(objectId, 1, shortcut(0, 0, ShortcutType.ACTION, 3, 0));

            assertThat(repository.findAll(objectId, 0)).containsExactly(action);
        }

        @Test
        void findAll_shouldLoseItemShortcut_whenItemDestroyed() {
            final UUID id = insertCharacter();
            final UUID itemId = insertItem(id, 1060, 5);
            repository.save(registry.handleOf(id), 0, shortcut(3, 1, ShortcutType.ITEM, registry.handleOf(itemId), 0));

            JDBC.sql("DELETE FROM items WHERE id = ?").param(itemId).update();

            assertThat(repository.findAll(registry.handleOf(id), 0)).isEmpty();
        }
    }

    @Nested
    class FindStored {
        @Test
        void findStored_shouldCarryTheItemIdAndAllocateNoHandle_whenTheShortcutIsAnItem() {
            final UUID id = insertCharacter();
            final UUID itemId = insertItem(id, 1060, 5);
            JDBC.sql("""
                INSERT INTO character_shortcuts (character_id, class_index, page, slot, shortcut_type, item_id)
                VALUES (?, 0, 1, 3, 'ITEM', ?)\
                """).params(id, itemId).update();
            final int allocatedBefore = registry.allocatedCount();

            final List<StoredShortcut> actual = repository.findStored(id, 0);

            assertThat(actual)
                    .containsExactly(StoredShortcut.builder()
                            .slot(3)
                            .page(1)
                            .type(ShortcutType.ITEM)
                            .itemId(itemId)
                            .build());
            assertThat(registry.allocatedCount()).isEqualTo(allocatedBefore);
        }

        @Test
        void findStored_shouldCarryTheTargetAndNoItemId_whenTheShortcutIsASkill() {
            final UUID id = insertCharacter();
            JDBC.sql("""
                INSERT INTO character_shortcuts (
                    character_id, class_index, page, slot, shortcut_type, target_id, skill_level)
                VALUES (?, 0, 0, 2, 'SKILL', 1177, 5)\
                """).param(id).update();

            assertThat(repository.findStored(id, 0))
                    .containsExactly(StoredShortcut.builder()
                            .slot(2)
                            .type(ShortcutType.SKILL)
                            .targetId(1177)
                            .level(5)
                            .build());
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyTheSlot() {
            final int objectId = registry.handleOf(insertCharacter());
            final CharacterShortcut kept = shortcut(1, 0, ShortcutType.ACTION, 2, 0);
            repository.save(objectId, 0, shortcut(0, 0, ShortcutType.ACTION, 1, 0));
            repository.save(objectId, 0, kept);

            repository.delete(objectId, 0, 0, 0);

            assertThat(repository.findAll(objectId, 0)).containsExactly(kept);
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveShortcutsOfTheClassIndexOnly() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, 0, shortcut(0, 0, ShortcutType.ACTION, 1, 0));
            repository.save(objectId, 1, shortcut(0, 0, ShortcutType.ACTION, 1, 0));

            repository.deleteAll(objectId, 1);

            assertThat(repository.findAll(objectId, 1)).isEmpty();
            assertThat(repository.findAll(objectId, 0)).hasSize(1);
        }

        @Test
        void deleteAll_shouldRemoveShortcutsOfEveryClassIndex() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, 0, shortcut(0, 0, ShortcutType.ACTION, 1, 0));
            repository.save(objectId, 1, shortcut(0, 0, ShortcutType.ACTION, 1, 0));

            repository.deleteAll(objectId);

            assertThat(repository.findAll(objectId, 0)).isEmpty();
            assertThat(repository.findAll(objectId, 1)).isEmpty();
        }
    }

    private static Map<String, Object> row(UUID characterId) {
        return JDBC.sql("SELECT * FROM character_shortcuts WHERE character_id = ?")
                .param(characterId)
                .query()
                .singleRow();
    }

    static CharacterShortcut shortcut(int slot, int page, ShortcutType type, int id, int level) {
        return CharacterShortcut.builder()
                .slot(slot)
                .page(page)
                .type(type)
                .id(id)
                .level(level)
                .build();
    }
}
