/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.data.holders.SiegableHallTemplate;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SiegableHallDataTest {

    private static final SiegableHallData DATA = SiegableHallData.getInstance();

    @Nested
    class Load {

        @Test
        void load_shouldReadEveryImportedHall() {
            assertThat(DATA.getHalls()).hasSize(6);
        }

        @Test
        void load_shouldReadEveryImportedSiegeGuard() {
            final int actual = DATA.getHalls().stream()
                    .mapToInt(hall -> hall.siegeGuards().size())
                    .sum();

            assertThat(actual).isEqualTo(729);
        }

        @Test
        void load_shouldReadHallAttributesAndSchedule() {
            final SiegableHallTemplate actual = DATA.getHall(21);

            assertThat(actual.name()).isEqualTo("Fortress of Resistance");
            assertThat(actual.location()).isEqualTo("Dion");
            assertThat(actual.siegeLength()).isEqualTo(Duration.ofHours(1));
            assertThat(actual.scheduleDays()).isEqualTo(14);
            assertThat(actual.scheduleMonths()).isZero();
            assertThat(actual.scheduleYears()).isZero();
            assertThat(actual.siegeHour()).isEqualTo(12);
            assertThat(actual.siegeMinute()).isZero();
        }
    }
}
