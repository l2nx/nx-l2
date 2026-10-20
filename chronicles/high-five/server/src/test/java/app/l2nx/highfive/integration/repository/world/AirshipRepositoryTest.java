/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.world;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.world.AirshipRepository;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AirshipRepositoryTest extends RepositoryTest {
    private final AirshipRepository repository = new AirshipRepository(JDBC, registry);

    @Nested
    class Save {
        @Test
        void save_shouldStoreClanUuid_whenLicenseIsBought() {
            final UUID clan = insertClan();

            repository.save(registry.handleOf(clan), 600);

            assertThat(JDBC.sql("SELECT fuel FROM airships WHERE clan_id = ?")
                            .param(clan)
                            .query(Integer.class)
                            .single())
                    .isEqualTo(600);
        }

        @Test
        void save_shouldUpdateFuel_whenLicenseExists() {
            final int clan = registry.handleOf(insertClan());
            repository.save(clan, 600);

            repository.save(clan, 150);

            assertThat(repository.findAll()).containsEntry(clan, 150);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldKeyFuelByClanHandle_whenStored() {
            final UUID clan = insertClan();
            JDBC.sql("INSERT INTO airships (clan_id, fuel) VALUES (?, 42)")
                    .param(clan)
                    .update();

            assertThat(repository.findAll()).containsEntry(registry.handleOf(clan), 42);
        }
    }
}
