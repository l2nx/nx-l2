/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.minigame;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.minigame.LotteryRepository;
import app.l2nx.highfive.repository.minigame.model.Lottery;
import app.l2nx.highfive.repository.minigame.model.LotteryResult;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("lotteries")
class LotteryRepositoryTest extends RepositoryTest {
    private static final Instant END_DATE = NOW;
    private static final LotteryResult RESULT = LotteryResult.builder()
            .number1(0b10110)
            .number2(0b1001)
            .prize1(3_000_000_000L)
            .prize2(500_000)
            .prize3(100_000)
            .build();

    private final LotteryRepository repository = new LotteryRepository(JDBC);

    @Nested
    class Create {
        @Test
        void create_shouldStartRoundWithNextNumber() {
            final int previous = repository.create(END_DATE, 1000, 1000);

            final int actual = repository.create(END_DATE, 2000, 2000);

            assertThat(actual).isGreaterThan(previous);
            assertThat(repository.findLast())
                    .contains(Lottery.builder()
                            .id(actual)
                            .prize(2000)
                            .newPrize(2000)
                            .endDate(END_DATE)
                            .finished(false)
                            .build());
        }
    }

    @Nested
    class UpdatePrize {
        @Test
        void updatePrize_shouldStorePools() {
            final int id = repository.create(END_DATE, 1000, 1000);

            repository.updatePrize(id, 1500, 1500);

            assertThat(repository.findLast())
                    .get()
                    .extracting(Lottery::prize, Lottery::newPrize)
                    .containsExactly(1500L, 1500L);
        }
    }

    @Nested
    class Finish {
        @Test
        void finish_shouldStoreDrawAndMarkFinished() {
            final int id = repository.create(END_DATE, 1000, 1000);

            repository.finish(id, 3_000_600_000L, 200_000, RESULT);

            assertThat(repository.findResult(id)).contains(RESULT);
            assertThat(repository.findLast())
                    .get()
                    .extracting(Lottery::finished, Lottery::prize, Lottery::newPrize)
                    .containsExactly(true, 3_000_600_000L, 200_000L);
        }
    }

    @Nested
    class FindResult {
        @Test
        void findResult_shouldBeEmpty_whenNotFinished() {
            final int id = repository.create(END_DATE, 1000, 1000);

            assertThat(repository.findResult(id)).isEmpty();
        }
    }

    @Nested
    class FindLast {
        @Test
        void findLast_shouldReturnLatestRound() {
            repository.create(END_DATE, 1, 1);
            final int latest = repository.create(END_DATE, 2, 2);

            assertThat(repository.findLast()).get().extracting(Lottery::id).isEqualTo(latest);
        }
    }
}
