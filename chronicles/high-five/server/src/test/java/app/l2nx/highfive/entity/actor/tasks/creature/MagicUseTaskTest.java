/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.tasks.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.ArrayList;
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

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class MagicUseTaskTest {
    private final Creature caster = mock(Creature.class);
    private final WorldObject target = mock(WorldObject.class);

    private MagicUseTask task() {
        return new MagicUseTask(caster, new ArrayList<>(List.of(target)), mock(Skill.class), 500, false);
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<MagicUseTask> call) {
            final MagicUseTask task = new MagicUseTask(mock(Creature.class), List.of(), mock(Skill.class), 500, false);

            assertThatThrownBy(() -> call.accept(task))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<MagicUseTask>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getTargets", MagicUseTask::getTargets),
                    Named.of("setTargets", t -> t.setTargets(List.of())),
                    Named.of("getSkill (accessor)", MagicUseTask::getSkill),
                    Named.of("getCount (accessor)", MagicUseTask::getCount),
                    Named.of("setCount", t -> t.setCount(1)),
                    Named.of("getSkillTime (accessor)", MagicUseTask::getSkillTime),
                    Named.of("setSkillTime", t -> t.setSkillTime(0)),
                    Named.of("getPhase (accessor)", MagicUseTask::getPhase),
                    Named.of("setPhase", t -> t.setPhase(2)),
                    Named.of("isSimultaneous (accessor)", MagicUseTask::isSimultaneous),
                    Named.of("run", MagicUseTask::run));
        }
    }

    @Nested
    class GetTargets {
        @Test
        void getTargets_shouldBeReadOnly() {
            final MagicUseTask task = task();

            final List<WorldObject> actual = WorldLoopExtension.callInPass(task::getTargets);

            assertThat(actual).containsExactly(target);
            assertThatThrownBy(() -> actual.add(target)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void setTargets_shouldReplaceTheTargets_whenGivenNull() {
            final MagicUseTask task = task();

            final List<WorldObject> actual = WorldLoopExtension.callInPass(() -> {
                task.setTargets(null);
                return task.getTargets();
            });

            assertThat(actual).isNull();
        }
    }

    @Nested
    class Run {
        @ParameterizedTest(name = "phase {0}")
        @CsvSource({"1", "2", "3"})
        void run_shouldCallTheCasterForThePhase(int phase) {
            final MagicUseTask task = task();

            WorldLoopExtension.runInPass(() -> {
                task.setPhase(phase);
                task.run();
            });

            switch (phase) {
                case 1 -> verify(caster).onMagicLaunchedTimer(task);
                case 2 -> verify(caster).onMagicHitTimer(task);
                default -> verify(caster).onMagicFinalizer(task);
            }
        }
    }
}
