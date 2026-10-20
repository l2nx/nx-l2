/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.item;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.item.GroundItemRepository;
import app.l2nx.highfive.repository.item.model.GroundItem;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("ground_items")
class GroundItemRepositoryTest extends RepositoryTest {
    private final GroundItemRepository repository = new GroundItemRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldStoreSnapshotAndReadItBack() {
            final GroundItem dropped = item(NOW, false);
            final GroundItem protectedItem = item(null, true);
            final GroundItem exempt =
                    item(null, false).toBuilder().count(5_000_000_000L).build();
            repository.replaceAll(List.of(item(NOW, false)));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(List.of(dropped, protectedItem, exempt)));

            assertThat(repository.findAll()).containsExactlyInAnyOrder(dropped, protectedItem, exempt);
        }

        @Test
        void replaceAll_shouldStoreDropTime() {
            final GroundItem dropped = item(NOW, false);

            repository.replaceAll(List.of(dropped));

            assertThat(JDBC.sql("SELECT dropped_at FROM ground_items WHERE id = ?")
                            .param(registry.idOf(dropped.objectId()))
                            .query((rs, rowNum) -> JdbcTimes.instant(rs, "dropped_at"))
                            .single())
                    .isEqualTo(NOW);
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldEmptyTheTable() {
            repository.replaceAll(List.of(item(null, false)));

            repository.deleteAll();

            assertThat(repository.findAll()).isEmpty();
        }
    }

    private GroundItem item(Instant dropTime, boolean protectedItem) {
        return GroundItem.builder()
                .objectId(registry.allocateTransient())
                .itemId(57)
                .count(10)
                .enchantLevel(0)
                .x(100)
                .y(-200)
                .z(-3000)
                .dropTime(dropTime)
                .protectedItem(protectedItem)
                .build();
    }
}
