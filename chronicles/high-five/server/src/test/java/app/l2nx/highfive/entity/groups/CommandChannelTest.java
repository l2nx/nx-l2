/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExCloseMPCC;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExMPCCPartyInfoUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExOpenMPCC;
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
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class CommandChannelTest {
    private final Player firstLeader = player(268_300_001, "FirstLeader");
    private final Player firstMember = player(268_300_002, "FirstMember");
    private final Player secondLeader = player(268_300_011, "SecondLeader");
    private final Player secondMember = player(268_300_012, "SecondMember");
    private final Player thirdLeader = player(268_300_021, "ThirdLeader");
    private final Player thirdMember = player(268_300_022, "ThirdMember");

    private static Player player(int objectId, String name) {
        final Player player = mock(Player.class);
        when(player.getObjectId()).thenReturn(objectId);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static Party party(Player leader, Player member) {
        final Party party = new Party(leader, PartyDistributionType.FINDERS_KEEPERS);
        party.addPartyMember(member);
        when(leader.getParty()).thenReturn(party);
        return party;
    }

    private record Channel(CommandChannel channel, Party first, Party second, Party third) {}

    private Channel channelOfThree() {
        return WorldLoopExtension.callInPass(() -> {
            final Party first = party(firstLeader, firstMember);
            final Party second = party(secondLeader, secondMember);
            final Party third = party(thirdLeader, thirdMember);
            final CommandChannel channel = new CommandChannel(firstLeader);
            channel.addParty(second);
            channel.addParty(third);
            return new Channel(channel, first, second, third);
        });
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<CommandChannel> call) {
            final CommandChannel channel = channelOfThree().channel();

            assertThatThrownBy(() -> call.accept(channel))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<CommandChannel>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getParties (accessor)", CommandChannel::getParties),
                    Named.of("getPartyCount", CommandChannel::getPartyCount),
                    Named.of("collectParties", c -> c.collectParties(new ArrayList<>())),
                    Named.of("collectMembers", c -> c.collectMembers(new ArrayList<>())),
                    Named.of("getLeader (accessor)", CommandChannel::getLeader),
                    Named.of("getLevel (accessor)", CommandChannel::getLevel),
                    Named.of("getMembers", CommandChannel::getMembers),
                    Named.of("getMemberCount", CommandChannel::getMemberCount),
                    Named.of("containsPlayer", c -> c.containsPlayer(null)),
                    Named.of("forEachMember", c -> c.forEachMember(_ -> true)),
                    Named.of("addParty", c -> c.addParty(null)),
                    Named.of("removeParty", c -> c.removeParty(null)),
                    Named.of("disbandChannel", CommandChannel::disbandChannel),
                    Named.of("setLeader", c -> c.setLeader(null)),
                    Named.of("meetRaidWarCondition", c -> c.meetRaidWarCondition(null)));
        }
    }

    @Nested
    class GetParties {
        @Test
        void getParties_shouldBeAReadOnlySnapshotInJoiningOrder() {
            final Channel channel = channelOfThree();

            final List<Party> actual = WorldLoopExtension.callInPass(() -> {
                final List<Party> snapshot = channel.channel().getParties();
                channel.channel().removeParty(channel.second());
                return snapshot;
            });

            assertThat(actual).containsExactly(channel.first(), channel.second(), channel.third());
            assertThatThrownBy(() -> actual.add(channel.first())).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void collectParties_shouldAppendThePartiesInJoiningOrder() {
            final Channel channel = channelOfThree();

            final List<Party> actual = WorldLoopExtension.callInPass(() -> {
                final List<Party> target = new ArrayList<>();
                channel.channel().collectParties(target);
                return target;
            });

            assertThat(actual).containsExactly(channel.first(), channel.second(), channel.third());
        }

        @Test
        void addParty_shouldChangeNothing_whenThePartyIsAlreadyInTheChannel() {
            final Channel channel = channelOfThree();

            final int actual = WorldLoopExtension.callInPass(() -> {
                channel.channel().addParty(channel.second());
                return channel.channel().getPartyCount();
            });

            assertThat(actual).isEqualTo(3);
            verify(secondMember, times(1)).sendPacket(ExOpenMPCC.STATIC_PACKET);
            verify(firstMember, times(2)).sendPacket(isA(ExMPCCPartyInfoUpdate.class));
        }
    }

    @Nested
    class Members {
        @Test
        void getMembers_shouldListThePartiesInJoiningOrderEachLeaderFirst() {
            final CommandChannel channel = channelOfThree().channel();

            final List<Player> actual = WorldLoopExtension.callInPass(channel::getMembers);

            assertThat(actual)
                    .containsExactly(firstLeader, firstMember, secondLeader, secondMember, thirdLeader, thirdMember);
        }

        @Test
        void getMemberCount_shouldSumTheParties() {
            final CommandChannel channel = channelOfThree().channel();

            final int actual = WorldLoopExtension.callInPass(channel::getMemberCount);

            assertThat(actual).isEqualTo(6);
        }

        @Test
        void containsPlayer_shouldFindAMemberOfAnyParty() {
            final CommandChannel channel = channelOfThree().channel();
            final Player stranger = player(268_300_099, "Stranger");

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> List.of(
                    channel.containsPlayer(thirdMember),
                    channel.containsPlayer(firstLeader),
                    channel.containsPlayer(stranger)));

            assertThat(actual).containsExactly(true, true, false);
        }

        @Test
        void forEachMember_shouldWalkEveryMember_whenTheProcedureRemovesAParty() {
            final Channel channel = channelOfThree();

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                final List<Player> visited = new ArrayList<>();
                channel.channel().forEachMember(member -> {
                    visited.add(member);
                    if (member == firstLeader) {
                        channel.channel().removeParty(channel.second());
                    }
                    return true;
                });
                return visited;
            });

            assertThat(actual)
                    .containsExactly(firstLeader, firstMember, secondLeader, secondMember, thirdLeader, thirdMember);
        }
    }

    @Nested
    class Disband {
        @Test
        void removeParty_shouldDisbandTheChannel_whenOnePartyIsLeft() {
            final Channel channel = channelOfThree();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                channel.channel().removeParty(channel.third());
                channel.channel().removeParty(channel.second());
                return List.of(
                        channel.channel().getPartyCount(),
                        channel.first().isInCommandChannel(),
                        channel.second().isInCommandChannel(),
                        channel.third().isInCommandChannel());
            });

            assertThat(actual).containsExactly(0, false, false, false);
        }

        @Test
        void disbandChannel_shouldCloseTheWindowOfEveryPartyOnce() {
            final Channel channel = channelOfThree();

            final int actual = WorldLoopExtension.callInPass(() -> {
                channel.channel().disbandChannel();
                return channel.channel().getPartyCount();
            });

            assertThat(actual).isZero();
            for (Player member : List.of(firstLeader, firstMember, secondLeader, thirdMember)) {
                verify(member, times(1)).sendPacket(isA(ExCloseMPCC.class));
            }
        }
    }
}
