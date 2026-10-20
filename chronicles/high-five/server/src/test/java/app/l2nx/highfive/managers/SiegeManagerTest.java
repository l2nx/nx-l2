/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.repository.residence.CastleTrapUpgradeRepository;
import app.l2nx.highfive.repository.siege.CastleSiegeClanRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SiegeManagerTest {
    private static final int CASTLE_ID = 3;
    private static final int OWNER_CLAN_ID = 268_435_460;

    private final CastleSiegeClanRepository siegeClans = mock(CastleSiegeClanRepository.class);
    private final SiegeManager manager = new SiegeManager(siegeClans, mock(CastleTrapUpgradeRepository.class));

    @Nested
    class DeleteSiegeClans {
        @Test
        void deleteSiegeClans_shouldDropOwnerRegistrationsAtEveryCastle_whenCastleHasOwner() {
            manager.deleteSiegeClans(CASTLE_ID, OWNER_CLAN_ID);

            verify(siegeClans).deleteByCastle(CASTLE_ID);
            verify(siegeClans).deleteByClan(OWNER_CLAN_ID);
        }

        @Test
        void deleteSiegeClans_shouldKeepOtherCastlesRegistrations_whenCastleHasNoOwner() {
            manager.deleteSiegeClans(CASTLE_ID, 0);

            verify(siegeClans).deleteByCastle(CASTLE_ID);
            verify(siegeClans, never()).deleteByClan(anyInt());
        }
    }
}
