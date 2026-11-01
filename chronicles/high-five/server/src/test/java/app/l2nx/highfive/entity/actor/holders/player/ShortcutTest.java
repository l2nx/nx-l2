/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
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
class ShortcutTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Shortcut> call) {
            final Shortcut shortcut = new Shortcut(1, 0, ShortcutType.ITEM, 268_000_001, 0, 1);

            assertThatThrownBy(() -> call.accept(shortcut))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Shortcut>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getSlot", Shortcut::getSlot),
                    Named.of("getPage", Shortcut::getPage),
                    Named.of("getType", Shortcut::getType),
                    Named.of("getId", Shortcut::getId),
                    Named.of("getLevel", Shortcut::getLevel),
                    Named.of("getCharacterType", Shortcut::getCharacterType),
                    Named.of("getSharedReuseGroup", Shortcut::getSharedReuseGroup),
                    Named.of("setSharedReuseGroup", shortcut -> shortcut.setSharedReuseGroup(3)));
        }
    }

    @Nested
    class SharedReuseGroup {
        @Test
        void getSharedReuseGroup_shouldAnswerNoGroup_whenNoneIsSet() {
            final Shortcut shortcut = new Shortcut(1, 0, ShortcutType.ITEM, 268_000_001, 0, 1);

            final int actual = WorldLoopExtension.callInPass(shortcut::getSharedReuseGroup);

            assertThat(actual).isEqualTo(-1);
        }

        @Test
        void setSharedReuseGroup_shouldReplaceTheGroup() {
            final Shortcut shortcut = new Shortcut(1, 0, ShortcutType.ITEM, 268_000_001, 0, 1);

            final int actual = WorldLoopExtension.callInPass(() -> {
                shortcut.setSharedReuseGroup(3);
                return shortcut.getSharedReuseGroup();
            });

            assertThat(actual).isEqualTo(3);
        }
    }
}
