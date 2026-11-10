/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.item;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ItemAugmentationRepositoryTest extends RepositoryTest {
    private final ItemAugmentationRepository repository = new ItemAugmentationRepository(JDBC, registry);

    @Nested
    class Find {
        @Test
        void find_shouldReturnAugmentation() {
            final UUID item = insertItem(insertCharacter(), 1, 1);
            JDBC.sql("INSERT INTO item_augmentations (item_id, augmentation_id) VALUES (?, 77)")
                    .param(item)
                    .update();

            assertThat(repository.find(registry.handleOf(item))).contains(77);
        }

        @Test
        void find_shouldBeEmpty_whenNotAugmented() {
            final UUID item = insertItem(insertCharacter(), 1, 1);

            assertThat(repository.find(registry.handleOf(item))).isEmpty();
        }
    }

    @Nested
    class FindByOwner {
        @Test
        void findByOwner_shouldGiveTheAugmentationOfEveryItemOfTheOwner() {
            final UUID owner = insertCharacter();
            final UUID first = insertItem(owner, 6_579, 1);
            final UUID second = insertItem(owner, 6_580, 1);
            insertItem(owner, 6_582, 1);
            final UUID foreign = insertItem(insertCharacter(), 6_581, 1);
            JDBC.sql("INSERT INTO item_augmentations (item_id, augmentation_id) VALUES (?, ?), (?, ?), (?, ?)")
                    .params(first, 101, second, 102, foreign, 103)
                    .update();

            assertThat(repository.findByOwner(owner))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(first, 101, second, 102));
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldInsertThenReplace() {
            final int item = registry.handleOf(insertItem(insertCharacter(), 1, 1));

            repository.save(item, 10);
            repository.save(item, 20);

            assertThat(repository.find(item)).contains(20);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveAugmentation() {
            final int item = registry.handleOf(insertItem(insertCharacter(), 1, 1));
            repository.save(item, 10);

            repository.delete(item);

            assertThat(repository.find(item)).isEmpty();
        }
    }
}
