/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.residence;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.residence.CastleStateRepository;
import app.l2nx.highfive.repository.residence.model.CastleState;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

// resetTaxOfUnownedCastles touches every row of the table.
@Execution(ExecutionMode.SAME_THREAD)
class CastleStateRepositoryTest extends RepositoryTest {
    private static final AtomicInteger CASTLE_IDS = new AtomicInteger(1000);

    private final CastleStateRepository repository = new CastleStateRepository(JDBC, registry);

    private static int newCastleId() {
        return CASTLE_IDS.incrementAndGet();
    }

    private static void insertState(int castleId, UUID ownerId, int taxPercent) {
        JDBC.sql("INSERT INTO castle_states (castle_id, owner_clan_id, tax_percent) VALUES (?, ?, ?)")
                .param(castleId)
                .param(ownerId)
                .param(taxPercent)
                .update();
    }

    private static Map<String, Object> row(int castleId) {
        return JDBC.sql("SELECT * FROM castle_states WHERE castle_id = ?")
                .param(castleId)
                .query()
                .singleRow();
    }

    @Nested
    class FindById {
        @Test
        void findById_shouldReturnEmpty_whenNoStateRow() {
            assertThat(repository.findById(newCastleId())).isEmpty();
        }

        @Test
        void findById_shouldReturnZeroSentinels_whenColumnsAreNull() {
            final int castleId = newCastleId();
            insertState(castleId, null, 0);

            final CastleState actual = repository.findById(castleId).orElseThrow();

            assertThat(actual)
                    .isEqualTo(CastleState.builder()
                            .castleId(castleId)
                            .registrationOver(true)
                            .build());
        }

        @Test
        void findById_shouldReturnOwnerHandleAndTimes_whenSet() {
            final int castleId = newCastleId();
            final UUID clanId = insertClan();
            final Instant registrationEnd = NOW.minusSeconds(3600);
            JDBC.sql("""
                INSERT INTO castle_states (
                    castle_id, owner_clan_id, tax_percent, treasury, siege_at, registration_ends_at,
                    registration_over, show_npc_crest, ticket_buy_count)
                VALUES (?, ?, 15, 5000, ?, ?, false, true, 3)\
                """)
                    .param(castleId)
                    .param(clanId)
                    .param(JdbcTimes.utc(NOW))
                    .param(JdbcTimes.utc(registrationEnd))
                    .update();

            final CastleState actual = repository.findById(castleId).orElseThrow();

            assertThat(actual)
                    .isEqualTo(CastleState.builder()
                            .castleId(castleId)
                            .ownerClanId(registry.handleOf(clanId))
                            .taxPercent(15)
                            .treasury(5000)
                            .siegeDate(NOW)
                            .registrationEndDate(registrationEnd)
                            .registrationOver(false)
                            .showNpcCrest(true)
                            .ticketBuyCount(3)
                            .build());
        }
    }

    @Nested
    class FindCastleIdByOwner {
        @Test
        void findCastleIdByOwner_shouldReturnCastle_whenClanOwnsIt() {
            final int castleId = newCastleId();
            final UUID clanId = insertClan();
            insertState(castleId, clanId, 0);

            assertThat(repository.findCastleIdByOwner(registry.handleOf(clanId)))
                    .hasValue(castleId);
        }

        @Test
        void findCastleIdByOwner_shouldReturnEmpty_whenClanOwnsNoCastle() {
            assertThat(repository.findCastleIdByOwner(registry.handleOf(insertClan())))
                    .isEmpty();
        }

        @Test
        void findCastleIdByOwner_shouldReturnEmpty_whenHandleIsNoStoredEntity() {
            assertThat(repository.findCastleIdByOwner(registry.allocateTransient()))
                    .isEmpty();
        }
    }

    @Nested
    class UpdateOwner {
        @Test
        void updateOwner_shouldCreateRowWithDefaults_whenNoState() {
            final int castleId = newCastleId();
            final UUID clanId = insertClan();

            repository.updateOwner(castleId, registry.handleOf(clanId));

            assertThat(row(castleId))
                    .containsEntry("owner_clan_id", clanId)
                    .containsEntry("tax_percent", 0)
                    .containsEntry("registration_over", true);
        }

        @Test
        void updateOwner_shouldStoreNull_whenOwnerIsZero() {
            final int castleId = newCastleId();
            insertState(castleId, insertClan(), 10);

            repository.updateOwner(castleId, 0);

            assertThat(row(castleId)).containsEntry("owner_clan_id", null).containsEntry("tax_percent", 10);
        }

        @Test
        void updateOwner_shouldReleasePreviousCastle_whenClanOwnsAnother() {
            final int previousCastleId = newCastleId();
            final int castleId = newCastleId();
            final UUID clanId = insertClan();
            insertState(previousCastleId, clanId, 0);

            repository.updateOwner(castleId, registry.handleOf(clanId));

            assertThat(row(previousCastleId)).containsEntry("owner_clan_id", null);
            assertThat(row(castleId)).containsEntry("owner_clan_id", clanId);
        }
    }

    @Nested
    class UpdateTreasury {
        @Test
        void updateTreasury_shouldCreateRow_whenNoState() {
            final int castleId = newCastleId();

            repository.updateTreasury(castleId, 7000);

            assertThat(row(castleId)).containsEntry("treasury", 7000L);
        }

        @Test
        void updateTreasury_shouldKeepOtherColumns_whenRowExists() {
            final int castleId = newCastleId();
            insertState(castleId, null, 20);

            repository.updateTreasury(castleId, 8000);

            assertThat(row(castleId)).containsEntry("treasury", 8000L).containsEntry("tax_percent", 20);
        }
    }

    @Nested
    class UpdateTaxPercent {
        @Test
        void updateTaxPercent_shouldUpsertTax() {
            final int castleId = newCastleId();

            repository.updateTaxPercent(castleId, 25);
            repository.updateTaxPercent(castleId, 5);

            assertThat(row(castleId)).containsEntry("tax_percent", 5);
        }
    }

    @Nested
    class UpdateShowNpcCrest {
        @Test
        void updateShowNpcCrest_shouldUpsertFlag() {
            final int castleId = newCastleId();

            repository.updateShowNpcCrest(castleId, true);

            assertThat(row(castleId)).containsEntry("show_npc_crest", true);
        }
    }

    @Nested
    class UpdateTicketBuyCount {
        @Test
        void updateTicketBuyCount_shouldUpsertCount() {
            final int castleId = newCastleId();

            repository.updateTicketBuyCount(castleId, 4);

            assertThat(row(castleId)).containsEntry("ticket_buy_count", 4);
        }
    }

    @Nested
    class UpdateSiegeSchedule {
        @Test
        void updateSiegeSchedule_shouldStoreTimes() {
            final int castleId = newCastleId();
            final Instant registrationEnd = NOW.minusSeconds(60);

            repository.updateSiegeSchedule(castleId, NOW, registrationEnd, false);

            assertThat(repository.findById(castleId).orElseThrow())
                    .extracting(CastleState::siegeDate, CastleState::registrationEndDate, CastleState::registrationOver)
                    .containsExactly(NOW, registrationEnd, false);
        }

        @Test
        void updateSiegeSchedule_shouldStoreNull_whenTimesAreZero() {
            final int castleId = newCastleId();

            repository.updateSiegeSchedule(castleId, null, null, true);

            assertThat(row(castleId)).containsEntry("siege_at", null).containsEntry("registration_ends_at", null);
        }
    }

    @Nested
    class ResetTaxOfUnownedCastles {
        @Test
        void resetTaxOfUnownedCastles_shouldResetOnlyCastlesWithoutOwner() {
            final int unownedCastleId = newCastleId();
            final int ownedCastleId = newCastleId();
            insertState(unownedCastleId, null, 30);
            insertState(ownedCastleId, insertClan(), 30);

            repository.resetTaxOfUnownedCastles();

            assertThat(row(unownedCastleId)).containsEntry("tax_percent", 0);
            assertThat(row(ownedCastleId)).containsEntry("tax_percent", 30);
        }
    }
}
