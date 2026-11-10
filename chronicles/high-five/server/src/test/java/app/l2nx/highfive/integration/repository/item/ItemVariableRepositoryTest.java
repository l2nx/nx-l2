/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.item;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ItemVariableRepositoryTest extends RepositoryTest {
    private final ItemVariableRepository repository = new ItemVariableRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class Save {
        @Test
        void save_shouldInsertAndUpdateVariables() {
            final int item = newItem();
            repository.save(item, List.of(), Map.of("transmogId", "100", "owner", "x"));

            TRANSACTIONS.executeWithoutResult(status -> repository.save(item, List.of(), Map.of("transmogId", "200")));

            assertThat(repository.findAll(item))
                    .containsExactly(Map.entry("owner", "x"), Map.entry("transmogId", "200"));
        }

        @Test
        void save_shouldDeleteRemovedAndEmptiedVariables() {
            final int item = newItem();
            repository.save(item, List.of(), Map.of("a", "1", "b", "2", "c", "3"));
            final Map<String, String> changed = new HashMap<>();
            changed.put("b", "");
            changed.put("c", null);

            repository.save(item, List.of("a"), changed);

            assertThat(repository.findAll(item)).isEmpty();
        }
    }

    @Nested
    class Exists {
        @Test
        void exists_shouldBeTrue_whenItemHasVariables() {
            final int item = newItem();
            repository.save(item, List.of(), Map.of("a", "1"));

            assertThat(repository.exists(item)).isTrue();
        }

        @Test
        void exists_shouldBeFalse_whenItemHasNoVariables() {
            assertThat(repository.exists(newItem())).isFalse();
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnOnlyTheItemsVariables() {
            final int item = newItem();
            repository.save(item, List.of(), Map.of("a", "1"));
            repository.save(newItem(), List.of(), Map.of("b", "2"));

            assertThat(repository.findAll(item)).containsExactly(Map.entry("a", "1"));
        }
    }

    @Nested
    class FindByOwner {
        @Test
        void findByOwner_shouldGiveTheVariablesOfEveryItemOfTheOwner() {
            final UUID owner = insertCharacter();
            final UUID item = insertItem(owner, 6_579, 1);
            final UUID foreign = insertItem(insertCharacter(), 6_579, 1);
            JDBC.sql("""
                INSERT INTO item_variables (item_id, name, content)
                VALUES (?, 'transmogId', '6580'), (?, 'transmogId', '6581')\
                """).params(item, foreign).update();

            assertThat(repository.findByOwner(owner)).containsExactly(Map.entry(item, Map.of("transmogId", "6580")));
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveEveryVariable() {
            final int item = newItem();
            repository.save(item, List.of(), Map.of("a", "1", "b", "2"));

            repository.deleteAll(item);

            assertThat(repository.findAll(item)).isEmpty();
        }
    }

    private int newItem() {
        return registry.handleOf(insertItem(insertCharacter(), 1, 1));
    }
}
