/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.repository.minigame.MonsterRaceRepository;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MonsterRaceManagerTest {
    private final MonsterRaceRepository races = mock(MonsterRaceRepository.class);
    private final MonsterRaceManager manager = new MonsterRaceManager(races);

    @Nested
    class LoadBets {
        @Test
        void loadBets_shouldGiveEveryLaneAnOdd_whenOnlySomeLanesHaveStoredBets() {
            when(races.findBets()).thenReturn(Map.of(3, 1_000L));

            manager.loadBets();
            manager.calculateOdds();

            assertThat(manager.getOdds()).containsExactly(0D, 0D, 1.25, 0D, 0D, 0D, 0D, 0D);
        }
    }
}
