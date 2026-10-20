/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.siege;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.mechanics.siege.SiegeClanType;
import app.l2nx.highfive.repository.siege.CastleSiegeClanRepository;
import app.l2nx.highfive.repository.siege.model.CastleSiegeClan;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CastleSiegeClanRepositoryTest extends RepositoryTest {
    private static final AtomicInteger CASTLE_IDS = new AtomicInteger(13000);

    private final CastleSiegeClanRepository repository = new CastleSiegeClanRepository(JDBC, registry);

    private static void insertSiegeClan(int castleId, UUID clanId, SiegeClanType side) {
        JDBC.sql("INSERT INTO castle_siege_clans (castle_id, clan_id, side) VALUES (?, ?, ?)")
                .param(castleId)
                .param(clanId)
                .param(side.name())
                .update();
    }

    private static long count(int castleId) {
        return JDBC.sql("SELECT count(*) FROM castle_siege_clans WHERE castle_id = ?")
                .param(castleId)
                .query(Long.class)
                .single();
    }

    @Nested
    class FindByCastle {
        @ParameterizedTest
        @EnumSource(SiegeClanType.class)
        void findByCastle_shouldReturnClanHandleAndSide(SiegeClanType side) {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertSiegeClan(castleId, clanId, side);

            assertThat(repository.findByCastle(castleId))
                    .containsExactly(CastleSiegeClan.builder()
                            .clanId(registry.handleOf(clanId))
                            .side(side)
                            .build());
        }
    }

    @Nested
    class Exists {
        @Test
        void exists_shouldReturnTrue_whenClanIsRegistered() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertSiegeClan(castleId, clanId, SiegeClanType.ATTACKER);

            assertThat(repository.exists(castleId, registry.handleOf(clanId))).isTrue();
        }

        @Test
        void exists_shouldReturnFalse_whenClanIsRegisteredForAnotherCastle() {
            final UUID clanId = insertClan();
            insertSiegeClan(CASTLE_IDS.incrementAndGet(), clanId, SiegeClanType.ATTACKER);

            assertThat(repository.exists(CASTLE_IDS.incrementAndGet(), registry.handleOf(clanId)))
                    .isFalse();
        }

        @Test
        void exists_shouldReturnFalse_whenHandleIsNoStoredEntity() {
            assertThat(repository.exists(CASTLE_IDS.incrementAndGet(), registry.allocateTransient()))
                    .isFalse();
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreClanUuidAndSide() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final UUID clanId = insertClan();

            repository.insert(castleId, registry.handleOf(clanId), SiegeClanType.DEFENDER_PENDING);

            assertThat(JDBC.sql("SELECT clan_id, side FROM castle_siege_clans WHERE castle_id = ?")
                            .param(castleId)
                            .query()
                            .singleRow())
                    .containsEntry("clan_id", clanId)
                    .containsEntry("side", "DEFENDER_PENDING");
        }
    }

    @Nested
    class UpdateSide {
        @Test
        void updateSide_shouldChangeSide() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertSiegeClan(castleId, clanId, SiegeClanType.DEFENDER_PENDING);

            repository.updateSide(castleId, registry.handleOf(clanId), SiegeClanType.DEFENDER);

            assertThat(repository.findByCastle(castleId))
                    .extracting(CastleSiegeClan::side)
                    .containsExactly(SiegeClanType.DEFENDER);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyGivenClan() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertSiegeClan(castleId, clanId, SiegeClanType.ATTACKER);
            insertSiegeClan(castleId, insertClan(), SiegeClanType.ATTACKER);

            repository.delete(castleId, registry.handleOf(clanId));

            assertThat(count(castleId)).isEqualTo(1);
        }
    }

    @Nested
    class DeleteByCastle {
        @Test
        void deleteByCastle_shouldRemoveRegistrationsOfCastleOnly() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final int otherCastleId = CASTLE_IDS.incrementAndGet();
            insertSiegeClan(castleId, insertClan(), SiegeClanType.ATTACKER);
            insertSiegeClan(otherCastleId, insertClan(), SiegeClanType.ATTACKER);

            repository.deleteByCastle(castleId);

            assertThat(count(castleId)).isZero();
            assertThat(count(otherCastleId)).isEqualTo(1);
        }
    }

    @Nested
    class DeleteByCastleAndSide {
        @Test
        void deleteByCastleAndSide_shouldRemoveOnlyClansOfSide() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            insertSiegeClan(castleId, insertClan(), SiegeClanType.DEFENDER_PENDING);
            insertSiegeClan(castleId, insertClan(), SiegeClanType.DEFENDER);

            repository.deleteByCastleAndSide(castleId, SiegeClanType.DEFENDER_PENDING);

            assertThat(repository.findByCastle(castleId))
                    .extracting(CastleSiegeClan::side)
                    .containsExactly(SiegeClanType.DEFENDER);
        }
    }

    @Nested
    class DeleteByClan {
        @Test
        void deleteByClan_shouldRemoveClanFromEveryCastle() {
            final int castleId = CASTLE_IDS.incrementAndGet();
            final int otherCastleId = CASTLE_IDS.incrementAndGet();
            final UUID clanId = insertClan();
            insertSiegeClan(castleId, clanId, SiegeClanType.ATTACKER);
            insertSiegeClan(otherCastleId, clanId, SiegeClanType.DEFENDER);
            insertSiegeClan(otherCastleId, insertClan(), SiegeClanType.ATTACKER);

            repository.deleteByClan(registry.handleOf(clanId));

            assertThat(count(castleId)).isZero();
            assertThat(count(otherCastleId)).isEqualTo(1);
        }
    }
}
