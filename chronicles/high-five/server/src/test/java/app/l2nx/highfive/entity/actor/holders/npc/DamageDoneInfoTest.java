/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.partition.WorldLoopExtension;
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
class DamageDoneInfoTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<DamageDoneInfo> call) {
            final DamageDoneInfo info = new DamageDoneInfo(mock(Player.class));

            assertThatThrownBy(() -> call.accept(info))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<DamageDoneInfo>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getAttacker (accessor)", DamageDoneInfo::getAttacker),
                    Named.of("getDamage (accessor)", DamageDoneInfo::getDamage),
                    Named.of("addDamage", info -> info.addDamage(1)));
        }

        @Test
        void equalsAndHashCode_shouldReadFieldsOnly_whenCalledOffTheLoop() {
            final Player attacker = mock(Player.class);

            final DamageDoneInfo actual = new DamageDoneInfo(attacker);

            assertThat(actual).isEqualTo(new DamageDoneInfo(attacker)).hasSameHashCodeAs(new DamageDoneInfo(attacker));
        }
    }

    @Nested
    class AddDamage {
        @Test
        void addDamage_shouldSumTheDamage() {
            final DamageDoneInfo info = new DamageDoneInfo(mock(Player.class));

            final long actual = WorldLoopExtension.callInPass(() -> {
                info.addDamage(40);
                info.addDamage(2);
                return info.getDamage();
            });

            assertThat(actual).isEqualTo(42);
        }
    }
}
