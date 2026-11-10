/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterItemReuseRepository;
import app.l2nx.highfive.repository.character.model.CharacterItemReuse;
import app.l2nx.highfive.repository.character.model.StoredItemReuse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterItemReuseRepositoryTest extends RepositoryTest {
    private final CharacterItemReuseRepository repository =
            new CharacterItemReuseRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldStoreItemInstancesAndDropPreviousRows() {
            final UUID id = insertCharacter();
            final int objectId = registry.handleOf(id);
            final CharacterItemReuse first = reuse(insertItem(id, 1060, 5), 1060);
            final CharacterItemReuse second = reuse(insertItem(id, 1061, 5), 1061);
            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(objectId, List.of(first)));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(objectId, List.of(second)));

            assertThat(repository.findAll(objectId)).containsExactly(second);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldResolveItemHandles() {
            final UUID id = insertCharacter();
            final UUID itemId = insertItem(id, 1060, 5);
            JDBC.sql("""
                INSERT INTO character_item_reuse_saves (
                    character_id, item_id, item_template_id, reuse_delay_ms, reuse_ends_at)
                VALUES (?, ?, 1060, 10000, ?)\
                """).param(id).param(itemId).param(JdbcTimes.utc(NOW)).update();

            final List<CharacterItemReuse> actual = repository.findAll(registry.handleOf(id));

            assertThat(actual).containsExactly(itemReuse(registry.handleOf(itemId), 1060, 10000, NOW));
        }
    }

    @Nested
    class FindStored {
        @Test
        void findStored_shouldCarryTheItemIdAndAllocateNoHandle() {
            final UUID id = insertCharacter();
            final UUID itemId = insertItem(id, 1060, 5);
            JDBC.sql("""
                INSERT INTO character_item_reuse_saves (
                    character_id, item_id, item_template_id, reuse_delay_ms, reuse_ends_at)
                VALUES (?, ?, 1060, 10000, ?)\
                """).param(id).param(itemId).param(JdbcTimes.utc(NOW)).update();
            final int allocatedBefore = registry.allocatedCount();

            final List<StoredItemReuse> actual = repository.findStored(id);

            assertThat(actual)
                    .containsExactly(StoredItemReuse.builder()
                            .itemId(itemId)
                            .itemTemplateId(1060)
                            .reuseDelayMs(10000)
                            .reuseEndsAt(NOW)
                            .build());
            assertThat(registry.allocatedCount()).isEqualTo(allocatedBefore);
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveEveryRowOfTheCharacter() {
            final UUID id = insertCharacter();
            final int objectId = registry.handleOf(id);
            final CharacterItemReuse reuse = reuse(insertItem(id, 1060, 5), 1060);
            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(objectId, List.of(reuse)));

            repository.deleteAll(objectId);

            assertThat(repository.findAll(objectId)).isEmpty();
        }
    }

    private CharacterItemReuse reuse(UUID itemId, int itemTemplateId) {
        return itemReuse(registry.handleOf(itemId), itemTemplateId, 10000, NOW.plusSeconds(10));
    }

    private static CharacterItemReuse itemReuse(
            int itemObjectId, int itemTemplateId, long reuseDelayMs, Instant reuseEndsAt) {
        return CharacterItemReuse.builder()
                .itemObjectId(itemObjectId)
                .itemTemplateId(itemTemplateId)
                .reuseDelayMs(reuseDelayMs)
                .reuseEndsAt(reuseEndsAt)
                .build();
    }
}
