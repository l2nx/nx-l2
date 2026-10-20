/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.olympiad;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.olympiad.OlympiadStatusRepository;
import app.l2nx.highfive.repository.olympiad.model.OlympiadStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("olympiad_status")
class OlympiadStatusRepositoryTest extends RepositoryTest {
    private final OlympiadStatusRepository repository = new OlympiadStatusRepository(JDBC);

    @BeforeEach
    void clear() {
        JDBC.sql("DELETE FROM olympiad_status").update();
    }

    @Nested
    class Find {
        @Test
        void find_shouldReturnEmpty_whenNeverSaved() {
            assertThat(repository.find()).isEmpty();
        }

        @Test
        void find_shouldReturnNullInstants_whenInstantsAreNull() {
            JDBC.sql("INSERT INTO olympiad_status (id, current_cycle, period) VALUES (0, 3, 'VALIDATION')")
                    .update();

            assertThat(repository.find())
                    .contains(OlympiadStatus.builder().currentCycle(3).period(1).build());
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldInsertStatus_whenNoRowExists() {
            final OlympiadStatus expected = OlympiadStatus.builder()
                    .currentCycle(2)
                    .period(0)
                    .olympiadEnd(NOW)
                    .validationEnd(null)
                    .nextWeeklyChange(NOW.plusSeconds(60))
                    .build();

            repository.save(expected);

            assertThat(repository.find()).contains(expected);
            assertThat(JDBC.sql("SELECT period FROM olympiad_status")
                            .query(String.class)
                            .single())
                    .isEqualTo("COMPETITION");
        }

        @Test
        void save_shouldOverwriteStatus_whenRowExists() {
            repository.save(
                    OlympiadStatus.builder().currentCycle(1).olympiadEnd(NOW).build());
            final OlympiadStatus expected = OlympiadStatus.builder()
                    .currentCycle(2)
                    .period(1)
                    .validationEnd(NOW)
                    .build();

            repository.save(expected);

            assertThat(repository.find()).contains(expected);
        }
    }
}
