/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Duration;
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
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class TimeStampTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<TimeStamp> call) {
            final TimeStamp stamp = new TimeStamp(skill(), 1_000, -1);

            assertThatThrownBy(() -> call.accept(stamp))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<TimeStamp>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getStamp (accessor)", TimeStamp::getStamp),
                    Named.of("getReuse (accessor)", TimeStamp::getReuse),
                    Named.of("getSkillId", TimeStamp::getSkillId),
                    Named.of("getRemaining", TimeStamp::getRemaining),
                    Named.of("hasNotPassed", TimeStamp::hasNotPassed));
        }
    }

    @Nested
    class Reuse {
        @Test
        void hasNotPassed_shouldClearTheStamp_whenTheReuseHasRunOut(ManualGameClock clock) {
            final TimeStamp stamp = new TimeStamp(skill(), 1_000, -1);

            final boolean before = WorldLoopExtension.callInPass(stamp::hasNotPassed);
            clock.advance(Duration.ofMillis(1_000));
            final List<Object> actual =
                    WorldLoopExtension.callInPass(() -> List.of(stamp.hasNotPassed(), stamp.getStamp()));

            assertThat(before).isTrue();
            assertThat(actual).containsExactly(false, 0L);
        }
    }

    private static Skill skill() {
        final Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(1);
        when(skill.getLevel()).thenReturn(1);
        return skill;
    }
}
