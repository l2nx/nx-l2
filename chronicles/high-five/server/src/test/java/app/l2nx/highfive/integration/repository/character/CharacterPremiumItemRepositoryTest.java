/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterPremiumItemRepository;
import app.l2nx.highfive.repository.character.model.CharacterPremiumItem;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterPremiumItemRepositoryTest extends RepositoryTest {
    private final CharacterPremiumItemRepository repository = new CharacterPremiumItemRepository(JDBC, registry);

    @Nested
    class FindByCharacter {
        @Test
        void findByCharacter_shouldReturnItemsOfThatCharacter() {
            final UUID character = insertCharacter();
            insertPremiumItem(character, 2, 57, 1000, "Shop");
            insertPremiumItem(character, 1, 4037, 5, "Event");
            insertPremiumItem(insertCharacter(), 1, 57, 1, "Shop");

            final List<CharacterPremiumItem> actual = repository.findByCharacter(registry.handleOf(character));

            assertThat(actual)
                    .containsExactly(
                            new CharacterPremiumItem(1, 4037, 5, "Event"),
                            new CharacterPremiumItem(2, 57, 1000, "Shop"));
        }
    }

    @Nested
    class UpdateCount {
        @Test
        void updateCount_shouldChangeOnlyThatItem() {
            final UUID character = insertCharacter();
            insertPremiumItem(character, 1, 57, 1000, "Shop");
            insertPremiumItem(character, 2, 57, 1000, "Shop");

            repository.updateCount(registry.handleOf(character), 1, 400);

            assertThat(items(character)).containsExactly(tuple(1, 400L), tuple(2, 1000L));
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyThatItem() {
            final UUID character = insertCharacter();
            insertPremiumItem(character, 1, 57, 1000, "Shop");
            insertPremiumItem(character, 2, 57, 1000, "Shop");

            repository.delete(registry.handleOf(character), 1);

            assertThat(items(character)).containsExactly(tuple(2, 1000L));
        }
    }

    private static void insertPremiumItem(UUID character, int itemNum, int templateId, long count, String sender) {
        JDBC.sql("""
            INSERT INTO character_premium_items (character_id, item_num, item_template_id, item_count, sender_name)
            VALUES (?, ?, ?, ?, ?)\
            """)
                .param(character)
                .param(itemNum)
                .param(templateId)
                .param(count)
                .param(sender)
                .update();
    }

    private static List<Tuple> items(UUID character) {
        return JDBC.sql("""
            SELECT item_num, item_count FROM character_premium_items WHERE character_id = ? ORDER BY item_num\
            """)
                .param(character)
                .query((rs, rowNum) -> tuple(rs.getInt("item_num"), rs.getLong("item_count")))
                .list();
    }
}
