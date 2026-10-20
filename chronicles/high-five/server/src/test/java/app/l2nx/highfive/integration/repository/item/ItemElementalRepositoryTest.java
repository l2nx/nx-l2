/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.item;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ItemElementalRepositoryTest extends RepositoryTest {
    private static final ItemElemental FIRE = elemental(0, 120);
    private static final ItemElemental WATER = elemental(1, 60);

    private final ItemElementalRepository repository = new ItemElementalRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldReplacePreviousElementals() {
            final int item = newItem();
            repository.replaceAll(item, List.of(FIRE));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(item, List.of(WATER, elemental(5, 10))));

            assertThat(repository.findAll(item)).containsExactly(WATER, elemental(5, 10));
        }

        @Test
        void replaceAll_shouldSkipNoneElement() {
            final int item = newItem();

            repository.replaceAll(item, List.of(elemental(-1, 30), elemental(2, -1), FIRE));

            assertThat(repository.findAll(item)).containsExactly(FIRE);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldBeEmpty_whenNoElementals() {
            assertThat(repository.findAll(newItem())).isEmpty();
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOneElement() {
            final int item = newItem();
            repository.replaceAll(item, List.of(FIRE, WATER));

            repository.delete(item, (byte) 0);

            assertThat(repository.findAll(item)).containsExactly(WATER);
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveEveryElement() {
            final int item = newItem();
            repository.replaceAll(item, List.of(FIRE, WATER));

            repository.deleteAll(item);

            assertThat(repository.findAll(item)).isEmpty();
        }
    }

    private int newItem() {
        return registry.handleOf(insertItem(insertCharacter(), 1, 1));
    }

    private static ItemElemental elemental(int element, int value) {
        return ItemElemental.builder().element((byte) element).value(value).build();
    }
}
