/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.minigame;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.minigame.MonsterRaceRepository;
import app.l2nx.highfive.repository.minigame.model.MonsterRaceResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("monster_race_bets")
class MonsterRaceRepositoryTest extends RepositoryTest {
    private static final AtomicInteger RACE_NUMBERS = new AtomicInteger(5000);

    private final MonsterRaceRepository repository = new MonsterRaceRepository(JDBC);

    @Nested
    class SaveResult {
        @Test
        void saveResult_shouldInsertThenReplace() {
            final MonsterRaceResult result = MonsterRaceResult.builder()
                    .raceId(RACE_NUMBERS.incrementAndGet())
                    .first(0)
                    .second(7)
                    .oddRate(0)
                    .build();
            repository.saveResult(result);
            final MonsterRaceResult finished =
                    result.toBuilder().first(3).second(5).oddRate(12.35).build();

            repository.saveResult(finished);

            assertThat(repository.findResults())
                    .filteredOn(row -> row.raceId() == result.raceId())
                    .containsExactly(finished);
        }
    }

    @Nested
    class FindResults {
        @Test
        void findResults_shouldOrderByRaceNumber() {
            final int later = RACE_NUMBERS.addAndGet(2);
            final int earlier = later - 1;
            repository.saveResult(result(later));
            repository.saveResult(result(earlier));

            assertThat(repository.findResults())
                    .extracting(MonsterRaceResult::raceId)
                    .containsSubsequence(earlier, later)
                    .isSorted();
        }

        private static MonsterRaceResult result(int raceId) {
            return MonsterRaceResult.builder()
                    .raceId(raceId)
                    .first(1)
                    .second(2)
                    .oddRate(1.5)
                    .build();
        }
    }

    @Nested
    class Bets {
        @Test
        void saveBet_shouldInsertThenReplaceLaneTotal() {
            repository.saveBet(3, 100);

            repository.saveBet(3, 250);

            assertThat(repository.findBets()).containsEntry(3, 250L);
        }

        @Test
        void clearBets_shouldResetEveryLane() {
            repository.saveBet(1, 100);
            repository.saveBet(8, 900);

            repository.clearBets();

            assertThat(repository.findBets()).containsEntry(1, 0L).containsEntry(8, 0L);
        }

        @Test
        void findBets_shouldReturnLanesInOrder() {
            repository.saveBet(5, 10);
            repository.saveBet(2, 20);

            assertThat(List.copyOf(repository.findBets().keySet())).isSorted().contains(2, 5);
        }
    }
}
