/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocationTest {
    @Nested
    class Mutators {
        @Test
        void setters_shouldWork_whenALoaderBuildsStaticDataOffTheLoop() {
            final Location location = new Location(0, 0, 0);

            location.setXYZ(1, 2, 3);
            location.setHeading(4);
            location.setInstanceId(5);

            assertThat(List.of(
                            location.getX(),
                            location.getY(),
                            location.getZ(),
                            location.getHeading(),
                            location.getInstanceId()))
                    .containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        void setHeading_shouldClampToTheUnsignedShortRange() {
            final Location location = new Location(0, 0, 0);

            location.setHeading(-1);
            final int negative = location.getHeading();
            location.setHeading(70_000);

            assertThat(List.of(negative, location.getHeading())).containsExactly(0, 65_535);
        }
    }

    @Nested
    class CopiesOf {
        @Test
        void copiesOf_shouldHandOutReadOnlyCopiesInOrder() {
            final List<Location> source = List.of(new Location(1, 2, 3), new Location(4, 5, 6));

            final List<Location> actual = Location.copiesOf(source);
            actual.getFirst().setXYZ(0, 0, 0);

            assertThat(source.getFirst()).isEqualTo(new Location(1, 2, 3));
            assertThat(actual).hasSize(2).last().isEqualTo(new Location(4, 5, 6));
            assertThatThrownBy(() -> actual.add(new Location(7, 8, 9)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void copiesOf_shouldBeNull_whenTheSourceIsNull() {
            assertThat(Location.copiesOf(null)).isNull();
        }
    }
}
