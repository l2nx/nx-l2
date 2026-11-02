/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.zone.type;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class EffectZoneTest {
    private static EffectZone zone() {
        return WorldLoopExtension.callInPass(() -> new EffectZone(70_401));
    }

    @Nested
    class GetSkillLevel {
        @Test
        void getSkillLevel_shouldBeZero_whenTheZoneHasNoSkills() {
            final EffectZone zone = zone();

            final int actual = WorldLoopExtension.callInPass(() -> zone.getSkillLevel(4515));

            assertThat(actual).isZero();
        }

        @ParameterizedTest(name = "skill {0} -> level {1}")
        @CsvSource({"4515, 3", "4516, 1", "4517, 0"})
        void getSkillLevel_shouldAnswerTheParsedLevel(int skillId, int expected) {
            final EffectZone zone = zone();

            final int actual = WorldLoopExtension.callInPass(() -> {
                zone.setParameter("skillIdLvl", "4515-3;4516-1");
                return zone.getSkillLevel(skillId);
            });

            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    class AddSkill {
        @Test
        void addSkill_shouldRemoveTheSkill_whenTheLevelIsBelowOne() {
            final EffectZone zone = zone();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                zone.addSkill(4515, 2);
                final int added = zone.getSkillLevel(4515);
                zone.addSkill(4515, 0);
                return List.of(added, zone.getSkillLevel(4515));
            });

            assertThat(actual).containsExactly(2, 0);
        }

        @Test
        void clearSkills_shouldDropEverySkill() {
            final EffectZone zone = zone();

            final int actual = WorldLoopExtension.callInPass(() -> {
                zone.addSkill(4515, 2);
                zone.addSkill(4516, 1);
                zone.clearSkills();
                return zone.getSkillLevel(4515) + zone.getSkillLevel(4516);
            });

            assertThat(actual).isZero();
        }
    }
}
