/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.HandysBlockCheckerManager;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ArenaParticipantsHolderTest {
    private static ArenaParticipantsHolder holder() {
        return WorldLoopExtension.callInPass(() -> new ArenaParticipantsHolder(0));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ArenaParticipantsHolder> call) {
            final ArenaParticipantsHolder holder = holder();

            assertThatThrownBy(() -> call.accept(holder))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ArenaParticipantsHolder>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getRedPlayers", ArenaParticipantsHolder::getRedPlayers),
                    Named.of("getBluePlayers", ArenaParticipantsHolder::getBluePlayers),
                    Named.of("getEvent (accessor)", ArenaParticipantsHolder::getEvent),
                    Named.of("getAllPlayers", ArenaParticipantsHolder::getAllPlayers),
                    Named.of("addPlayer", holder -> holder.addPlayer(null, 0)),
                    Named.of("removePlayer", holder -> holder.removePlayer(null, 0)),
                    Named.of("getPlayerTeam", holder -> holder.getPlayerTeam(null)),
                    Named.of("getRedTeamSize", ArenaParticipantsHolder::getRedTeamSize),
                    Named.of("getBlueTeamSize", ArenaParticipantsHolder::getBlueTeamSize),
                    Named.of("broadCastPacketToTeam", holder -> holder.broadCastPacketToTeam(null)),
                    Named.of("clearPlayers", ArenaParticipantsHolder::clearPlayers),
                    Named.of("updateEvent", ArenaParticipantsHolder::updateEvent),
                    Named.of("checkAndShuffle", ArenaParticipantsHolder::checkAndShuffle));
        }
    }

    @Nested
    class Teams {
        @Test
        void getAllPlayers_shouldReturnAReadOnlySnapshot_redFirst() {
            final ArenaParticipantsHolder holder = holder();
            final Player red = mock(Player.class);
            final Player blue = mock(Player.class);

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                holder.addPlayer(blue, 1);
                holder.addPlayer(red, 0);
                final List<Player> snapshot = holder.getAllPlayers();
                holder.removePlayer(red, 0);
                return snapshot;
            });

            assertThat(actual).containsExactly(red, blue);
            assertThatThrownBy(() -> actual.add(red)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getPlayerTeam_shouldAnswerTheTeamOrMinusOne() {
            final ArenaParticipantsHolder holder = holder();
            final Player red = mock(Player.class);
            final Player blue = mock(Player.class);

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                holder.addPlayer(red, 0);
                holder.addPlayer(blue, 1);
                return List.of(
                        holder.getPlayerTeam(red),
                        holder.getPlayerTeam(blue),
                        holder.getPlayerTeam(mock(Player.class)));
            });

            assertThat(actual).containsExactly(0, 1, -1);
        }
    }

    @Nested
    class CheckAndShuffle {
        @ParameterizedTest(name = "red {0}, blue {1} -> red {2}, blue {3}")
        @CsvSource({"4, 0, 2, 2", "5, 2, 4, 3", "1, 5, 3, 3", "3, 2, 3, 2"})
        void checkAndShuffle_shouldBalanceTheTeams(int red, int blue, int expectedRed, int expectedBlue) {
            final ArenaParticipantsHolder holder = holder();
            final HandysBlockCheckerManager manager = mock(HandysBlockCheckerManager.class);
            doAnswer(call -> {
                        final Player player = call.getArgument(0);
                        final int from = holder.getPlayerTeam(player);
                        holder.removePlayer(player, from);
                        holder.addPlayer(player, 1 - from);
                        return null;
                    })
                    .when(manager)
                    .changePlayerToTeam(any(Player.class), anyInt());

            final List<Integer> actual;
            try (MockedStatic<HandysBlockCheckerManager> managers = mockStatic(HandysBlockCheckerManager.class)) {
                managers.when(HandysBlockCheckerManager::getInstance).thenReturn(manager);
                actual = WorldLoopExtension.callInPass(() -> {
                    for (int i = 0; i < red; i++) {
                        holder.addPlayer(mock(Player.class), 0);
                    }

                    for (int i = 0; i < blue; i++) {
                        holder.addPlayer(mock(Player.class), 1);
                    }

                    holder.checkAndShuffle();
                    return List.of(holder.getRedTeamSize(), holder.getBlueTeamSize());
                });
            }

            assertThat(actual).containsExactly(expectedRed, expectedBlue);
        }
    }
}
