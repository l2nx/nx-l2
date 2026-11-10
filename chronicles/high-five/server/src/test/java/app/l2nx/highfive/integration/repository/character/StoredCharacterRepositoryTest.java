/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.character.StoredCharacter;
import app.l2nx.highfive.character.repository.StoredCharacterRepository;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.model.CharacterSkill;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import app.l2nx.highfive.repository.item.model.StoredItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StoredCharacterRepositoryTest extends RepositoryTest {
    private final StoredCharacterRepository repository = StoredCharacterRepositories.of(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class Find {
        @Test
        void find_shouldBeEmpty_whenTheCharacterHasNoRow() {
            assertThat(repository.find(UUID.randomUUID())).isEmpty();
        }

        @Test
        void find_shouldReadTheItemsOfEveryContainerWithTheirAttributes_andAllocateNoItemHandle() {
            final UUID character = insertCharacter();
            final UUID sword = insertItem(character, 6_579, 1);
            JDBC.sql("UPDATE items SET location = 'PAPERDOLL', location_slot = 7 WHERE id = ?")
                    .param(sword)
                    .update();
            final UUID stored = insertItem(character, 57, 10);
            JDBC.sql("UPDATE items SET location = 'WAREHOUSE', location_slot = NULL WHERE id = ?")
                    .param(stored)
                    .update();
            final UUID sent = insertItem(character, 1_060, 3);
            JDBC.sql("UPDATE items SET location = 'FREIGHT', location_slot = NULL WHERE id = ?")
                    .param(sent)
                    .update();
            JDBC.sql("INSERT INTO item_augmentations (item_id, augmentation_id) VALUES (?, 101)")
                    .param(sword)
                    .update();
            JDBC.sql("INSERT INTO item_elementals (item_id, element, element_value) VALUES (?, 0, 120)")
                    .param(sword)
                    .update();
            JDBC.sql("INSERT INTO item_variables (item_id, name, content) VALUES (?, 'transmogId', '6580')")
                    .param(sword)
                    .update();
            registry.handleOf(character);
            final int allocatedBefore = registry.allocatedCount();

            final StoredCharacter actual = repository.find(character).orElseThrow();

            assertThat(actual.inventory()).singleElement().satisfies(item -> {
                assertThat(item.id()).isEqualTo(sword);
                assertThat(item.location()).isEqualTo(ItemLocation.PAPERDOLL);
                assertThat(item.locationSlot()).isEqualTo(7);
                assertThat(item.augmentationId()).isEqualTo(101);
                assertThat(item.elementals())
                        .containsExactly(ItemElemental.builder()
                                .element((byte) 0)
                                .value(120)
                                .build());
                assertThat(item.variables()).containsExactly(Map.entry("transmogId", "6580"));
            });
            assertThat(actual.warehouse()).extracting(StoredItem::id).containsExactly(stored);
            assertThat(actual.freight()).extracting(StoredItem::id).containsExactly(sent);
            assertThat(registry.allocatedCount()).as("handles come on the loop").isEqualTo(allocatedBefore);
        }

        @Test
        void find_shouldReadTheRowsOfTheActiveClassIndex_whenTheActiveClassIsASubclass() {
            final UUID character = insertCharacter();
            JDBC.sql("UPDATE characters SET class_id = 12 WHERE id = ?")
                    .param(character)
                    .update();
            JDBC.sql("""
                INSERT INTO character_subclasses (character_id, class_index, class_id, exp, sp, class_level)
                VALUES (?, 1, 12, 0, 0, 40)\
                """).param(character).update();
            JDBC.sql("""
                INSERT INTO character_skills (character_id, class_index, skill_id, skill_level)
                VALUES (?, 0, 3, 1), (?, 1, 1177, 5)\
                """).params(character, character).update();

            final StoredCharacter actual = repository.find(character).orElseThrow();

            assertThat(actual.classIndex()).isEqualTo(1);
            assertThat(actual.skills()).extracting(CharacterSkill::skillId).containsExactly(1177);
        }

        @Test
        void find_shouldReadTheBaseClassRows_whenTheActiveSubclassRowIsMissing() {
            final UUID character = insertCharacter();
            JDBC.sql("UPDATE characters SET class_id = 12 WHERE id = ?")
                    .param(character)
                    .update();
            JDBC.sql("""
                INSERT INTO character_skills (character_id, class_index, skill_id, skill_level)
                VALUES (?, 0, 3, 1), (?, 1, 1177, 5)\
                """).params(character, character).update();

            final StoredCharacter actual = repository.find(character).orElseThrow();

            assertThat(actual.classIndex()).isZero();
            assertThat(actual.skills()).extracting(CharacterSkill::skillId).containsExactly(3);
        }

        @Test
        void find_shouldReadReferencesAsIds_andTheWorldEntryRows() {
            final UUID character = insertCharacter();
            final UUID friend = insertCharacter();
            final UUID blocked = insertCharacter();
            JDBC.sql("UPDATE characters SET name = 'Contact' WHERE id = ?")
                    .param(friend)
                    .update();
            JDBC.sql("""
                INSERT INTO character_relations (character_id, target_character_id, relation)
                VALUES (?, ?, 'FRIEND'), (?, ?, 'BLOCK')\
                """).params(character, friend, character, blocked).update();
            JDBC.sql("INSERT INTO character_contacts (character_id, contact_character_id) VALUES (?, ?)")
                    .params(character, friend)
                    .update();
            JDBC.sql("INSERT INTO character_variables (character_id, name, content) VALUES (?, 'EXPOFF', 'true')")
                    .param(character)
                    .update();
            JDBC.sql("""
                INSERT INTO character_quests (character_id, quest_name, name, content)
                VALUES (?, 'Q00001_LettersOfLove', '<state>', 'Started'), (?, 'Q00001_LettersOfLove', 'cond', '2')\
                """).params(character, character).update();
            JDBC.sql("""
                INSERT INTO character_recommendations (character_id, received, left_to_give, bonus_time_left_ms)
                VALUES (?, 4, 9, 3600000)\
                """).param(character).update();
            registry.handleOf(character);
            final int allocatedBefore = registry.allocatedCount();

            final StoredCharacter actual = repository.find(character).orElseThrow();

            assertThat(actual.friends()).containsExactly(friend);
            assertThat(actual.blocked()).containsExactly(blocked);
            assertThat(actual.contacts()).containsExactly("Contact");
            assertThat(actual.variables()).containsExactly(Map.entry("EXPOFF", "true"));
            assertThat(actual.worldEntry().questStates()).hasSize(1);
            assertThat(actual.worldEntry().questVariables()).hasSize(1);
            assertThat(actual.worldEntry().recommendation().received()).isEqualTo(4);
            assertThat(actual.worldEntry().premiumEndsAt()).isNull();
            assertThat(actual.worldEntry().transmogItemIds()).isEqualTo(List.of());
            assertThat(registry.allocatedCount())
                    .as("referenced characters get no handle")
                    .isEqualTo(allocatedBefore);
        }
    }
}
