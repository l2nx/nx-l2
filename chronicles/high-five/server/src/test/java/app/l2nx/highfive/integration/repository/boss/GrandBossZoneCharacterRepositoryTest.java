/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.boss;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.boss.GrandBossZoneCharacterRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("grand_boss_zone_characters")
class GrandBossZoneCharacterRepositoryTest extends RepositoryTest {
    private final GrandBossZoneCharacterRepository repository =
            new GrandBossZoneCharacterRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldStoreCharacterUuids_whenZonesHaveCharacters() {
            final UUID first = insertCharacter();
            final UUID second = insertCharacter();

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(
                    Map.of(12002, List.of(registry.handleOf(first), registry.handleOf(second)), 12003, List.of())));

            assertThat(JDBC.sql("SELECT character_id FROM grand_boss_zone_characters WHERE zone_id = 12002")
                            .query(UUID.class)
                            .list())
                    .containsExactlyInAnyOrder(first, second);
        }

        @Test
        void replaceAll_shouldDropPreviousRows_whenCalledAgain() {
            final UUID character = insertCharacter();
            TRANSACTIONS.executeWithoutResult(
                    status -> repository.replaceAll(Map.of(12004, List.of(registry.handleOf(character)))));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(Map.of()));

            assertThat(JDBC.sql("SELECT count(*) FROM grand_boss_zone_characters")
                            .query(Integer.class)
                            .single())
                    .isZero();
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldGroupHandlesByZone_whenRowsExist() {
            final UUID first = insertCharacter();
            final UUID second = insertCharacter();
            final UUID third = insertCharacter();
            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(Map.of(
                    12005, List.of(registry.handleOf(first), registry.handleOf(second)),
                    12006, List.of(registry.handleOf(third)))));

            final Map<Integer, List<Integer>> actual = repository.findAll();

            assertThat(actual).containsOnlyKeys(12005, 12006).containsEntry(12006, List.of(registry.handleOf(third)));
            assertThat(actual.get(12005))
                    .containsExactlyInAnyOrder(registry.handleOf(first), registry.handleOf(second));
        }
    }
}
