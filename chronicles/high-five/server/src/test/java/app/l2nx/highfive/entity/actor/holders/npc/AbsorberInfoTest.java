/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.npc;

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
class AbsorberInfoTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<AbsorberInfo> call) {
            final AbsorberInfo info = new AbsorberInfo(1, 100);

            assertThatThrownBy(() -> call.accept(info))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<AbsorberInfo>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getObjectId (accessor)", AbsorberInfo::getObjectId),
                    Named.of("setObjectId (accessor)", info -> info.setObjectId(2)),
                    Named.of("getAbsorbedHp (accessor)", AbsorberInfo::getAbsorbedHp),
                    Named.of("setAbsorbedHp (accessor)", info -> info.setAbsorbedHp(50)));
        }

        @Test
        void equalsAndHashCode_shouldReadFieldsOnly_whenCalledOffTheLoop() {
            final AbsorberInfo actual = new AbsorberInfo(1, 100);

            assertThat(actual)
                    .isEqualTo(new AbsorberInfo(1, 50))
                    .isNotEqualTo(new AbsorberInfo(2, 100))
                    .hasSameHashCodeAs(new AbsorberInfo(1, 50));
        }
    }

    @Nested
    class Accessors {
        @Test
        void setters_shouldReplaceTheValues_whenCalledOnTheLoop() {
            final AbsorberInfo info = new AbsorberInfo(1, 100);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                info.setObjectId(2);
                info.setAbsorbedHp(50);
                return List.of(info.getObjectId(), info.getAbsorbedHp());
            });

            assertThat(actual).containsExactly(2, 50.0);
        }
    }
}
