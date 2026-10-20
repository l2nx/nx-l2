/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.territory;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.territory.TerritoryRegistrationRepository;
import app.l2nx.highfive.repository.territory.model.TerritoryRegistrantType;
import app.l2nx.highfive.repository.territory.model.TerritoryRegistration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TerritoryRegistrationRepositoryTest extends RepositoryTest {
    private static final AtomicInteger CASTLE_IDS = new AtomicInteger(17000);

    private final TerritoryRegistrationRepository repository = new TerritoryRegistrationRepository(JDBC, registry);

    private static void insertRegistration(int castleId, UUID registrantId, TerritoryRegistrantType type) {
        JDBC.sql("INSERT INTO territory_registrations (castle_id, registrant_id, registrant_type) VALUES (?, ?, ?)")
                .param(castleId)
                .param(registrantId)
                .param(type.name())
                .update();
    }

    private static long count(int castleId) {
        return JDBC.sql("SELECT count(*) FROM territory_registrations WHERE castle_id = ?")
                .param(castleId)
                .query(Long.class)
                .single();
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnClansAndMercenariesWithTheirType() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            final UUID characterId = insertCharacter();
            insertRegistration(castleId, clanId, TerritoryRegistrantType.CLAN);
            insertRegistration(castleId, characterId, TerritoryRegistrantType.MERCENARY);

            assertThat(repository.findAll())
                    .contains(
                            TerritoryRegistration.builder()
                                    .castleId(castleId)
                                    .registrantId(registry.handleOf(clanId))
                                    .registrantType(TerritoryRegistrantType.CLAN)
                                    .build(),
                            TerritoryRegistration.builder()
                                    .castleId(castleId)
                                    .registrantId(registry.handleOf(characterId))
                                    .registrantType(TerritoryRegistrantType.MERCENARY)
                                    .build());
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreRegistrantUuidAndType() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final UUID characterId = insertCharacter();

            repository.insert(castleId, registry.handleOf(characterId), TerritoryRegistrantType.MERCENARY);

            assertThat(JDBC.sql("SELECT registrant_id, registrant_type FROM territory_registrations WHERE"
                                    + " castle_id = ?")
                            .param(castleId)
                            .query()
                            .singleRow())
                    .containsEntry("registrant_id", characterId)
                    .containsEntry("registrant_type", "MERCENARY");
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyGivenRegistrant() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertRegistration(castleId, clanId, TerritoryRegistrantType.CLAN);
            insertRegistration(castleId, insertClan(), TerritoryRegistrantType.CLAN);

            repository.delete(castleId, registry.handleOf(clanId));

            assertThat(count(castleId)).isEqualTo(1);
        }
    }
}
