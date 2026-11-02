/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.groups.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.entity.actor.Player;
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
class PartyMatchWaitingListTest {
    private final PartyMatchWaitingList list = new PartyMatchWaitingList();
    private final Player first = mock(Player.class);
    private final Player second = mock(Player.class);
    private final Player third = mock(Player.class);

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<PartyMatchWaitingList> call) {
            assertThatThrownBy(() -> call.accept(list))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<PartyMatchWaitingList>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getInstance (static)", _ -> PartyMatchWaitingList.getInstance()),
                    Named.of("getPlayers (accessor)", PartyMatchWaitingList::getPlayers),
                    Named.of("contains", l -> l.contains(null)),
                    Named.of("addPlayer", l -> l.addPlayer(null)),
                    Named.of("removePlayer", l -> l.removePlayer(null)));
        }
    }

    @Nested
    class GetPlayers {
        @Test
        void getPlayers_shouldBeAReadOnlySnapshotInJoiningOrder() {
            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                list.addPlayer(third);
                list.addPlayer(first);
                list.addPlayer(second);
                final List<Player> snapshot = list.getPlayers();
                list.removePlayer(first);
                return snapshot;
            });

            assertThat(actual).containsExactly(third, first, second);
            assertThatThrownBy(() -> actual.add(first)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void addPlayer_shouldKeepTheFirstPosition_whenThePlayerIsAlreadyWaiting() {
            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                list.addPlayer(first);
                list.addPlayer(second);
                list.addPlayer(first);
                return list.getPlayers();
            });

            assertThat(actual).containsExactly(first, second);
        }
    }

    @Nested
    class Contains {
        @Test
        void contains_shouldAnswerFromTheLiveSet() {
            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                list.addPlayer(first);
                final boolean added = list.contains(first);
                list.removePlayer(first);
                return List.of(added, list.contains(first), list.contains(second));
            });

            assertThat(actual).containsExactly(true, false, false);
        }
    }
}
