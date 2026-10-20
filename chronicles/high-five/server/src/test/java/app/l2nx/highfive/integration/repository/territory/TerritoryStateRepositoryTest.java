/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.territory;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.territory.TerritoryStateRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TerritoryStateRepositoryTest extends RepositoryTest {
    private static final AtomicInteger TERRITORY_IDS = new AtomicInteger(18000);

    private final TerritoryStateRepository repository = new TerritoryStateRepository(JDBC);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnStoredWards() {
            final int territoryId = TERRITORY_IDS.incrementAndGet();
            JDBC.sql("INSERT INTO territory_states (territory_id, owned_ward_territory_ids) VALUES (?, '{81,83}')")
                    .param(territoryId)
                    .update();

            assertThat(repository.findAll()).containsEntry(territoryId, List.of(81, 83));
        }

        @Test
        void findAll_shouldReturnEmptyList_whenTerritoryOwnsNoWard() {
            final int territoryId = TERRITORY_IDS.incrementAndGet();
            JDBC.sql("INSERT INTO territory_states (territory_id, owned_ward_territory_ids) VALUES (?, '{}')")
                    .param(territoryId)
                    .update();

            assertThat(repository.findAll()).containsEntry(territoryId, List.of());
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldCreateRow_whenNoState() {
            final int territoryId = TERRITORY_IDS.incrementAndGet();

            repository.save(territoryId, List.of(81, 82));

            assertThat(repository.findAll()).containsEntry(territoryId, List.of(81, 82));
        }

        @Test
        void save_shouldReplaceWards_whenRowExists() {
            final int territoryId = TERRITORY_IDS.incrementAndGet();
            JDBC.sql("INSERT INTO territory_states (territory_id, owned_ward_territory_ids) VALUES (?, '{81}')")
                    .param(territoryId)
                    .update();

            repository.save(territoryId, List.of());

            assertThat(repository.findAll()).containsEntry(territoryId, List.of());
        }
    }
}
