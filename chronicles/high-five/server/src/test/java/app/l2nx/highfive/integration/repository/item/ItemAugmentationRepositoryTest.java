/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.item;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
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
