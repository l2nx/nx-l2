/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class InvulSkillHolderTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<InvulSkillHolder> call) {
            final InvulSkillHolder holder = new InvulSkillHolder(1, 1);

            assertThatThrownBy(() -> call.accept(holder))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<InvulSkillHolder>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getInstances", InvulSkillHolder::getInstances),
                    Named.of("increaseInstances", InvulSkillHolder::increaseInstances),
                    Named.of("decreaseInstances", InvulSkillHolder::decreaseInstances));
        }
    }

    @Nested
    class Instances {
        @Test
        void decreaseInstances_shouldCountDownWhatWasCountedUp() {
            final InvulSkillHolder holder = new InvulSkillHolder(1, 1);

            final List<Integer> actual = WorldLoopExtension.callInPass(
                    () -> List.of(holder.increaseInstances(), holder.decreaseInstances(), holder.decreaseInstances()));

            assertThat(actual).containsExactly(2, 1, 0);
        }
    }
}
