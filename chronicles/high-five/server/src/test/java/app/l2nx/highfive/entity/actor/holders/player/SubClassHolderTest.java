/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

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
class SubClassHolderTest {
    private static final int CLASS_ID = 2;

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<SubClassHolder> call) {
            final SubClassHolder subClass = new SubClassHolder(CLASS_ID, 1000L, 50, (byte) 45, 1);

            assertThatThrownBy(() -> call.accept(subClass))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<SubClassHolder>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getId", SubClassHolder::getId),
                    Named.of("getPlayerClass", SubClassHolder::getPlayerClass),
                    Named.of("setPlayerClass", subClass -> subClass.setPlayerClass(CLASS_ID)),
                    Named.of("getExp", SubClassHolder::getExp),
                    Named.of("setExp", subClass -> subClass.setExp(1L)),
                    Named.of("getSp", SubClassHolder::getSp),
                    Named.of("setSp", subClass -> subClass.setSp(1L)),
                    Named.of("getLevel", SubClassHolder::getLevel),
                    Named.of("setLevel", subClass -> subClass.setLevel((byte) 50)),
                    Named.of("getClassIndex", SubClassHolder::getClassIndex),
                    Named.of("setClassIndex", subClass -> subClass.setClassIndex(2)),
                    Named.of("incLevel", SubClassHolder::incLevel),
                    Named.of("decLevel", SubClassHolder::decLevel));
        }
    }

    @Nested
    class Accessors {
        @Test
        void constructor_shouldKeepTheStoredValues() {
            final SubClassHolder subClass = new SubClassHolder(CLASS_ID, 1000L, 50, (byte) 45, 3);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> List.of(
                    subClass.getId(),
                    subClass.getExp(),
                    subClass.getSp(),
                    subClass.getLevel(),
                    subClass.getClassIndex()));

            assertThat(actual).containsExactly(CLASS_ID, 1000L, 50L, (byte) 45, 3);
        }

        @Test
        void setSp_shouldReplaceTheSp() {
            final SubClassHolder subClass = new SubClassHolder(CLASS_ID, 1000L, 50, (byte) 45, 3);

            final long actual = WorldLoopExtension.callInPass(() -> {
                subClass.setSp(70L);
                return subClass.getSp();
            });

            assertThat(actual).isEqualTo(70L);
        }
    }
}
