/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.seed.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.bot.model.CharacterTeam;
import app.l2nx.load.bot.model.Position;
import java.awt.Polygon;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CharacterPlanTest {
    private static final int BOTS = 500;
    // The nodes of the zone colosseum_battle1 in data/zones/pvp.xml.
    private static final int[] ZONE_X = {151252, 151219, 150677, 148334, 147748, 147744, 148238, 150781};
    private static final int[] ZONE_Y = {45918, 46182, 46786, 46788, 46166, 45916, 45301, 45307};
    private static final int ZONE_MIN_Z = -3498;
    private static final int ZONE_MAX_Z = -3298;

    @Nested
    class Spread {
        @Test
        void position_shouldStayInsideTheArenaZone_forEveryLoadBot() {
            final Polygon zone = new Polygon(ZONE_X, ZONE_Y, ZONE_X.length);

            assertThat(IntStream.range(0, BOTS).mapToObj(index -> CharacterPlan.ARENA.position(index, BOTS)))
                    .allSatisfy(position -> {
                        assertThat(zone.contains(position.x(), position.y()))
                                .as("%s", position)
                                .isTrue();
                        assertThat(position.z()).isBetween(ZONE_MIN_Z, ZONE_MAX_Z);
                    });
        }

        @ParameterizedTest
        @EnumSource(CharacterPlan.class)
        void position_shouldSpreadTheLoadBots(CharacterPlan plan) {
            final Set<Position> actual = new HashSet<>();
            IntStream.range(0, 100).forEach(index -> actual.add(plan.position(index, 100)));

            assertThat(actual).hasSize(100);
        }
    }

    @Nested
    class CharacterName {
        @ParameterizedTest
        @CsvSource({"ARENA, load7", "HUNTING, loadh7"})
        void characterName_shouldEndInTheIndex(CharacterPlan plan, String expected) {
            final String actual = plan.characterName("load", 7);

            assertThat(actual).isEqualTo(expected);
            assertThat(CharacterTeam.ofName(actual)).isEqualTo(CharacterTeam.ofIndex(7));
        }
    }

    @Nested
    class Slots {
        @Test
        void slot_shouldDiffer_soThatOneAccountHoldsACharacterPerPlan() {
            assertThat(CharacterPlan.ARENA.slot()).isNotEqualTo(CharacterPlan.HUNTING.slot());
        }
    }
}
