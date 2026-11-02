/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.entity.actor.Creature;
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
class AggroInfoTest {
    private static final long LIMIT = 1_000_000_000_000_000L;

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<AggroInfo> call) {
            final AggroInfo info = new AggroInfo(mock(Creature.class));

            assertThatThrownBy(() -> call.accept(info))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<AggroInfo>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getAttacker (accessor)", AggroInfo::getAttacker),
                    Named.of("getDamage (accessor)", AggroInfo::getDamage),
                    Named.of("getHate (accessor)", AggroInfo::getHate),
                    Named.of("addDamage", info -> info.addDamage(1)),
                    Named.of("addHate", info -> info.addHate(1)),
                    Named.of("stopHate", AggroInfo::stopHate),
                    Named.of("checkHate", info -> info.checkHate(null)));
        }

        @Test
        void equalsAndHashCode_shouldReadFieldsOnly_whenCalledOffTheLoop() {
            final Creature attacker = mock(Creature.class);

            final AggroInfo actual = new AggroInfo(attacker);

            assertThat(actual).isEqualTo(new AggroInfo(attacker)).hasSameHashCodeAs(new AggroInfo(attacker));
        }
    }

    @Nested
    class Counters {
        @Test
        void add_shouldCapPositiveValuesAndApplyNegativeOnes() {
            final AggroInfo info = new AggroInfo(mock(Creature.class));

            final List<Long> actual = WorldLoopExtension.callInPass(() -> {
                info.addHate(LIMIT - 1);
                info.addHate(10);
                info.addDamage(100);
                info.addDamage(-30);
                return List.of(info.getHate(), info.getDamage());
            });

            assertThat(actual).containsExactly(LIMIT, 70L);
        }

        @Test
        void checkHate_shouldResetHate_whenTheOwnerIsGone() {
            final AggroInfo info = new AggroInfo(mock(Creature.class));

            final List<Long> actual = WorldLoopExtension.callInPass(() -> {
                info.addHate(50);
                return List.of(info.checkHate(null), info.getHate());
            });

            assertThat(actual).containsExactly(0L, 0L);
        }
    }
}
