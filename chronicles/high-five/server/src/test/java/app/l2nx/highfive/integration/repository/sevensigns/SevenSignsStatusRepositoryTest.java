/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.sevensigns;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.sevensigns.SevenSignsStatusRepository;
import app.l2nx.highfive.repository.sevensigns.model.SevenSignsStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("seven_signs_status")
class SevenSignsStatusRepositoryTest extends RepositoryTest {
    private final SevenSignsStatusRepository repository = new SevenSignsStatusRepository(JDBC);

    @BeforeEach
    void reseed() {
        JDBC.sql("DELETE FROM seven_signs_status").update();
        JDBC.sql("""
            INSERT INTO seven_signs_status (id, current_cycle, festival_cycle, active_period)
            VALUES (0, 1, 1, 'COMPETITION')\
            """).update();
    }

    @Nested
    class Find {
        @Test
        void find_shouldReturnSeededDefaults_whenNeverUpdated() {
            final SevenSignsStatus actual = repository.find();

            assertThat(actual.activePeriod()).isEqualTo(1);
            assertThat(actual.savedAt()).isNull();
            assertThat(actual.previousWinner()).isZero();
            assertThat(actual.avariceOwner()).isZero();
            assertThat(actual.accumulatedBonuses()).containsExactly(0, 0, 0, 0, 0);
        }
    }

    @Nested
    class Update {
        @Test
        void update_shouldStoreEveryValue_whenCalled() {
            final SevenSignsStatus expected = SevenSignsStatus.builder()
                    .currentCycle(4)
                    .festivalCycle(7)
                    .activePeriod(3)
                    .savedAt(NOW)
                    .previousWinner(2)
                    .dawnStoneScore(1500)
                    .dawnFestivalScore(30)
                    .duskStoneScore(900)
                    .duskFestivalScore(20)
                    .avariceOwner(2)
                    .gnosisOwner(1)
                    .strifeOwner(0)
                    .avariceDawnScore(5)
                    .gnosisDawnScore(6)
                    .strifeDawnScore(7)
                    .avariceDuskScore(8)
                    .gnosisDuskScore(9)
                    .strifeDuskScore(10)
                    .accumulatedBonuses(List.of(1, 2, 3, 4, 5))
                    .build();

            repository.update(expected);

            assertThat(repository.find()).isEqualTo(expected);
            assertThat(JDBC.sql("""
                SELECT active_period || ',' || previous_winner || ',' || avarice_owner || ',' || gnosis_owner
                FROM seven_signs_status\
                """).query(String.class).single()).isEqualTo("SEAL_VALIDATION,DAWN,DAWN,DUSK");
        }
    }
}
