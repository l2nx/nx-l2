/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class CursedWeaponTest {
    private static final int ZARICHE = 8190;
    private static final int SKILL_ID = 3603;
    private static final int SKILL_MAX_LEVEL = 10;

    private static CursedWeapon weapon() {
        final SkillData skills = mock(SkillData.class);
        when(skills.getMaxLevel(SKILL_ID)).thenReturn(SKILL_MAX_LEVEL);
        try (MockedStatic<SkillData> skillData = mockStatic(SkillData.class)) {
            skillData.when(SkillData::getInstance).thenReturn(skills);
            return new CursedWeapon(ZARICHE, SKILL_ID, "Zariche");
        }
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<CursedWeapon> call) {
            final CursedWeapon weapon = weapon();

            assertThatThrownBy(() -> call.accept(weapon))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<CursedWeapon>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getName (accessor)", CursedWeapon::getName),
                    Named.of("setDropRate (accessor)", weapon -> weapon.setDropRate(1)),
                    Named.of("setStageKills (accessor)", weapon -> weapon.setStageKills(10)),
                    Named.of("isDropped (accessor)", CursedWeapon::isDropped),
                    Named.of("setActivated (accessor)", weapon -> weapon.setActivated(true)),
                    Named.of("setNbKills (accessor)", weapon -> weapon.setNbKills(1)),
                    Named.of("setEndTime (accessor)", weapon -> weapon.setEndTime(1L)),
                    Named.of("getPlayer (accessor)", CursedWeapon::getPlayer),
                    Named.of("setItem (accessor)", weapon -> weapon.setItem(null)),
                    Named.of("endOfLife", CursedWeapon::endOfLife),
                    Named.of("reActivate", CursedWeapon::reActivate),
                    Named.of("checkDrop", weapon -> weapon.checkDrop(null, null)),
                    Named.of("increaseKills", CursedWeapon::increaseKills),
                    Named.of("isActive", CursedWeapon::isActive),
                    Named.of("getLevel", CursedWeapon::getLevel),
                    Named.of("getTimeLeft", CursedWeapon::getTimeLeft),
                    Named.of("goTo", weapon -> weapon.goTo(null)),
                    Named.of("getWorldPosition", CursedWeapon::getWorldPosition),
                    Named.of("getDuration", CursedWeapon::getDuration));
        }
    }

    @Nested
    class GetLevel {
        @ParameterizedTest(name = "{0} kills -> level {1}")
        @CsvSource({"0, 0", "25, 2", "100, 10", "500, 10"})
        void getLevel_shouldFollowTheKillsUpToTheSkillMaximum(int kills, int expected) {
            final CursedWeapon weapon = weapon();

            final int actual = WorldLoopExtension.callInPass(() -> {
                weapon.setStageKills(10);
                weapon.setNbKills(kills);
                return weapon.getLevel();
            });

            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    class IsActive {
        @Test
        void isActive_shouldFollowTheActivatedAndDroppedStates() {
            final CursedWeapon weapon = weapon();

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                final boolean idle = weapon.isActive();
                weapon.setDropped(true);
                final boolean dropped = weapon.isActive();
                weapon.setDropped(false);
                weapon.setActivated(true);
                return List.of(idle, dropped, weapon.isActive());
            });

            assertThat(actual).containsExactly(false, true, true);
        }
    }
}
