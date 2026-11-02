/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.LocationUtil;
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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class PartyTest {
    private final Player leader = player(268_200_001, "Leader");
    private final Player second = player(268_200_002, "Second");
    private final Player third = player(268_200_003, "Third");

    private static Player player(int objectId, String name) {
        final Player player = mock(Player.class);
        when(player.getObjectId()).thenReturn(objectId);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private Party partyOfThree() {
        return WorldLoopExtension.callInPass(() -> {
            final Party party = new Party(leader, PartyDistributionType.FINDERS_KEEPERS);
            party.addPartyMember(second);
            party.addPartyMember(third);
            return party;
        });
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Party> call) {
            final Party party = partyOfThree();

            assertThatThrownBy(() -> call.accept(party))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Party>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getMembers", Party::getMembers),
                    Named.of("collectMembers", p -> p.collectMembers(new ArrayList<>())),
                    Named.of("getMembersObjectId", Party::getMembersObjectId),
                    Named.of("getMemberCount", Party::getMemberCount),
                    Named.of("getMember", p -> p.getMember(0)),
                    Named.of("containsPlayer", p -> p.containsPlayer(null)),
                    Named.of("getLeader", Party::getLeader),
                    Named.of("getLeaderObjectId", Party::getLeaderObjectId),
                    Named.of("isLeader", p -> p.isLeader(null)),
                    Named.of("getLevel (accessor)", Party::getLevel),
                    Named.of("getDistributionType (accessor)", Party::getDistributionType),
                    Named.of("getCommandChannel (accessor)", Party::getCommandChannel),
                    Named.of("setCommandChannel (accessor)", p -> p.setCommandChannel(null)),
                    Named.of("getDimensionalRift (accessor)", Party::getDimensionalRift),
                    Named.of("isInCommandChannel", Party::isInCommandChannel),
                    Named.of("getPendingInvitation", Party::getPendingInvitation),
                    Named.of("getRandomPlayer", Party::getRandomPlayer),
                    Named.of("forEachMember", p -> p.forEachMember(_ -> true)),
                    Named.of("broadcastPacket", p -> p.broadcastPacket(null)),
                    Named.of("broadcastToPartyMembers", p -> p.broadcastToPartyMembers(null, null)),
                    Named.of("addPartyMember", p -> p.addPartyMember(null)),
                    Named.of("removePartyMember", p -> p.removePartyMember((Player) null, PartyMessageType.LEFT)),
                    Named.of("setLeader", p -> p.setLeader(null)),
                    Named.of("getPlayerByName", p -> p.getPlayerByName("Leader")),
                    Named.of("distributeAdena", p -> p.distributeAdena(null, 1, null)),
                    Named.of("recalculatePartyLevel", Party::recalculatePartyLevel),
                    Named.of(
                            "requestLootChange (former synchronized)",
                            p -> p.requestLootChange(PartyDistributionType.RANDOM)),
                    Named.of(
                            "answerLootChangeRequest (former synchronized)",
                            p -> p.answerLootChangeRequest(null, true)),
                    Named.of("getUCState", Party::getUCState));
        }

        @Test
        void getMemberCount_shouldAnswer_whenCalledOnTheLoop() {
            final Party party = partyOfThree();

            final int actual = WorldLoopExtension.callInPass(party::getMemberCount);

            assertThat(actual).isEqualTo(3);
        }
    }

    @Nested
    class GetMembers {
        @Test
        void getMembers_shouldBeAReadOnlySnapshotInJoiningOrder() {
            final Party party =
                    WorldLoopExtension.callInPass(() -> new Party(leader, PartyDistributionType.FINDERS_KEEPERS));

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                party.addPartyMember(third);
                party.addPartyMember(second);
                final List<Player> snapshot = party.getMembers();
                party.removePartyMember(third, PartyMessageType.LEFT);
                return snapshot;
            });

            assertThat(actual).containsExactly(leader, third, second);
            assertThatThrownBy(() -> actual.add(leader)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getMembersObjectId_shouldListTheIdsInJoiningOrder() {
            final Party party = partyOfThree();

            final List<Integer> actual = WorldLoopExtension.callInPass(party::getMembersObjectId);

            assertThat(actual).containsExactly(268_200_001, 268_200_002, 268_200_003);
        }
    }

    @Nested
    class AddPartyMember {
        @Test
        void addPartyMember_shouldAddOnce_whenThePlayerIsAlreadyAMember() {
            final Party party = partyOfThree();

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                party.addPartyMember(second);
                return party.getMembers();
            });

            assertThat(actual).containsExactly(leader, second, third);
        }
    }

    @Nested
    class Lookups {
        @Test
        void getMember_shouldAnswerByPartyWindowPosition() {
            final Party party = partyOfThree();

            final List<Player> actual = WorldLoopExtension.callInPass(
                    () -> List.of(party.getMember(0), party.getMember(1), party.getMember(2)));

            assertThat(actual).containsExactly(leader, second, third);
        }

        @Test
        void containsPlayer_shouldAnswerFromTheLiveList() {
            final Party party = partyOfThree();
            final Player stranger = player(268_200_009, "Stranger");

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                final boolean before = party.containsPlayer(third);
                party.removePartyMember(third, PartyMessageType.LEFT);
                return List.of(before, party.containsPlayer(third), party.containsPlayer(stranger));
            });

            assertThat(actual).containsExactly(true, false, false);
        }

        @Test
        void isLeader_shouldAnswerForTheFirstMember() {
            final Party party = partyOfThree();

            final List<Boolean> actual =
                    WorldLoopExtension.callInPass(() -> List.of(party.isLeader(leader), party.isLeader(second)));

            assertThat(actual).containsExactly(true, false);
        }

        @Test
        void getRandomPlayer_shouldPickAMember() {
            final Party party = partyOfThree();

            final Player actual = WorldLoopExtension.callInPass(party::getRandomPlayer);

            assertThat(actual).isIn(leader, second, third);
        }

        @Test
        void getPlayerByName_shouldIgnoreCase() {
            final Party party = partyOfThree();

            final Player actual = WorldLoopExtension.callInPass(() -> party.getPlayerByName("third"));

            assertThat(actual).isSameAs(third);
        }
    }

    @Nested
    class CollectMembers {
        @Test
        void collectMembers_shouldAppendTheMembersInJoiningOrder() {
            final Party party = partyOfThree();

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                final List<Player> target = new ArrayList<>(List.of(party.getMember(2)));
                party.collectMembers(target);
                return target;
            });

            assertThat(actual).containsExactly(third, leader, second, third);
        }
    }

    @Nested
    class SetLeader {
        @Test
        void setLeader_shouldSwap_whenTheNewLeaderIsInAMatchRoomThatIsGone() {
            final Party party = partyOfThree();
            when(third.isInPartyMatchRoom()).thenReturn(true);

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                party.setLeader(third);
                return party.getMembers();
            });

            assertThat(actual).containsExactly(third, second, leader);
        }

        @Test
        void setLeader_shouldSwapTheNewLeaderWithTheOldOne() {
            final Party party = partyOfThree();

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                party.setLeader(third);
                return party.getMembers();
            });

            assertThat(actual).containsExactly(third, second, leader);
        }

        @Test
        void setLeader_shouldKeepTheOrder_whenThePlayerIsNotAMember() {
            final Party party = partyOfThree();
            final Player stranger = player(268_200_009, "Stranger");

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                party.setLeader(stranger);
                return party.getMembers();
            });

            assertThat(actual).containsExactly(leader, second, third);
            verify(stranger)
                    .sendPacket(SystemMessageId.YOU_MAY_ONLY_TRANSFER_PARTY_LEADERSHIP_TO_ANOTHER_MEMBER_OF_THE_PARTY);
        }
    }

    @Nested
    class RemovePartyMember {
        @Test
        void removePartyMember_shouldKeepTheOrderOfTheOthers() {
            final Party party = partyOfThree();

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                party.removePartyMember(second, PartyMessageType.LEFT);
                return party.getMembers();
            });

            assertThat(actual).containsExactly(leader, third);
            verify(second).setParty(null);
        }

        @Test
        void removePartyMember_shouldDisbandTheParty_whenTwoMembersAreLeft() {
            final Party party = WorldLoopExtension.callInPass(() -> {
                final Party pair = new Party(leader, PartyDistributionType.FINDERS_KEEPERS);
                pair.addPartyMember(second);
                return pair;
            });

            final int actual = WorldLoopExtension.callInPass(() -> {
                party.removePartyMember(second, PartyMessageType.LEFT);
                return party.getMemberCount();
            });

            assertThat(actual).isZero();
            verify(leader).setParty(null);
            verify(second).setParty(null);
        }
    }

    @Nested
    class DisbandInChannel {
        private final Player otherLeader = player(268_200_011, "OtherLeader");
        private final Player otherMember = player(268_200_012, "OtherMember");
        private final Player lastLeader = player(268_200_021, "LastLeader");
        private final Player lastMember = player(268_200_022, "LastMember");

        private record Channel(CommandChannel channel, Party other, Party last) {}

        private Party pair(Player first, Player next) {
            final Party pair = new Party(first, PartyDistributionType.FINDERS_KEEPERS);
            pair.addPartyMember(next);
            return pair;
        }

        private Channel channelLedBy(Party party) {
            return WorldLoopExtension.callInPass(() -> {
                when(leader.getParty()).thenReturn(party);
                final CommandChannel channel = new CommandChannel(leader);
                final Party other = pair(otherLeader, otherMember);
                final Party last = pair(lastLeader, lastMember);
                channel.addParty(other);
                channel.addParty(last);
                return new Channel(channel, other, last);
            });
        }

        @Test
        void disbandParty_shouldDisbandTheChannel_whenThePartyLedIt() {
            final Party party = partyOfThree();
            final Channel channel = channelLedBy(party);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                party.disbandParty();
                return List.of(
                        channel.channel().getPartyCount(),
                        channel.other().isInCommandChannel(),
                        channel.last().isInCommandChannel());
            });

            assertThat(actual).containsExactly(0, false, false);
        }

        @Test
        void disbandParty_shouldKeepTheChannelLeader_whenTheLeaderMayLeaveTheParty() {
            final Party party = partyOfThree();
            final Channel channel = channelLedBy(party);
            final boolean leaderMayLeave = PlayerConfig.ALT_LEAVE_PARTY_LEADER;
            PlayerConfig.ALT_LEAVE_PARTY_LEADER = true;

            final List<Object> actual;
            try {
                actual = WorldLoopExtension.callInPass(() -> {
                    party.disbandParty();
                    return List.of(
                            channel.channel().getLeader(), channel.channel().getPartyCount());
                });
            } finally {
                PlayerConfig.ALT_LEAVE_PARTY_LEADER = leaderMayLeave;
            }

            assertThat(actual).containsExactly(leader, 0);
        }
    }

    @Nested
    class ChannelLeadership {
        @Test
        void removePartyMember_shouldPassTheChannelToTheNewLeader_whenTheChannelLeaderDisconnects() {
            final Party party = partyOfThree();
            when(leader.getParty()).thenReturn(party);

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                final CommandChannel channel = new CommandChannel(leader);
                party.removePartyMember(leader, PartyMessageType.DISCONNECTED);
                return List.of(party.getLeader(), channel.getLeader());
            });

            assertThat(actual).containsExactly(second, second);
        }

        @Test
        void setLeader_shouldPassTheChannelToTheNewLeader_whenTheOldOneLedIt() {
            final Party party = partyOfThree();
            when(leader.getParty()).thenReturn(party);

            final Player actual = WorldLoopExtension.callInPass(() -> {
                final CommandChannel channel = new CommandChannel(leader);
                party.setLeader(third);
                return channel.getLeader();
            });

            assertThat(actual).isSameAs(third);
        }
    }

    @Nested
    class ForEachMember {
        @Test
        void forEachMember_shouldWalkTheMembersAtTheStart_whenTheProcedureChangesTheParty() {
            final Party party = partyOfThree();
            final Player late = player(268_200_004, "Late");

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                final List<Player> visited = new ArrayList<>();
                party.forEachMember(member -> {
                    visited.add(member);
                    if (member == leader) {
                        party.addPartyMember(late);
                        party.removePartyMember(second, PartyMessageType.LEFT);
                    }
                    return true;
                });
                return visited;
            });

            assertThat(actual).containsExactly(leader, second, third);
        }

        @Test
        void forEachMember_shouldStop_whenTheProcedureReturnsFalse() {
            final Party party = partyOfThree();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final List<Player> visited = new ArrayList<>();
                final boolean completed = party.forEachMember(member -> {
                    visited.add(member);
                    return member != second;
                });
                return List.of(completed, visited);
            });

            assertThat(actual).containsExactly(false, List.of(leader, second));
        }
    }

    @Nested
    class DistributeAdena {
        @Test
        void distributeAdena_shouldSplitTheWholeAmountGivingTheRemainderOneEach() {
            final Party party = partyOfThree();
            final Creature target = mock(Creature.class);

            try (MockedStatic<LocationUtil> location = mockStatic(LocationUtil.class)) {
                location.when(() -> LocationUtil.checkIfInRange(anyInt(), any(), any(), anyBoolean()))
                        .thenReturn(true);
                WorldLoopExtension.runInPass(() -> party.distributeAdena(leader, 11, target));
            }

            final List<Long> actual = new ArrayList<>();
            for (Player member : List.of(leader, second, third)) {
                final ArgumentCaptor<Long> amount = ArgumentCaptor.forClass(Long.class);
                verify(member).addAdena(eq(ItemProcessType.LOOT), amount.capture(), any(WorldObject.class), eq(true));
                actual.add(amount.getValue());
            }

            assertThat(actual).containsOnly(3L, 4L).filteredOn(a -> a == 4L).hasSize(2);
            assertThat(actual.stream().mapToLong(Long::longValue).sum()).isEqualTo(11L);
        }

        @Test
        void distributeAdena_shouldPayOnlyTheMembersInRange() {
            final Party party = partyOfThree();
            final Creature target = mock(Creature.class);

            try (MockedStatic<LocationUtil> location = mockStatic(LocationUtil.class)) {
                location.when(() -> LocationUtil.checkIfInRange(anyInt(), any(), any(), anyBoolean()))
                        .thenAnswer(call -> call.getArgument(2) != second);
                WorldLoopExtension.runInPass(() -> party.distributeAdena(leader, 10, target));
            }

            verify(leader).addAdena(ItemProcessType.LOOT, 5, leader, true);
            verify(third).addAdena(ItemProcessType.LOOT, 5, leader, true);
            verify(second, never())
                    .addAdena(any(ItemProcessType.class), anyLong(), any(WorldObject.class), anyBoolean());
        }
    }

    @Nested
    class LootChange {
        @Test
        void answerLootChangeRequest_shouldChangeTheDistribution_whenEveryOtherMemberAgrees() {
            final Party party = partyOfThree();

            final PartyDistributionType actual = WorldLoopExtension.callInPass(() -> {
                party.requestLootChange(PartyDistributionType.RANDOM);
                party.answerLootChangeRequest(second, true);
                party.answerLootChangeRequest(third, true);
                return party.getDistributionType();
            });

            assertThat(actual).isEqualTo(PartyDistributionType.RANDOM);
        }

        @Test
        void answerLootChangeRequest_shouldKeepTheDistribution_whenAMemberRefuses() {
            final Party party = partyOfThree();

            final PartyDistributionType actual = WorldLoopExtension.callInPass(() -> {
                party.requestLootChange(PartyDistributionType.RANDOM);
                party.answerLootChangeRequest(second, true);
                party.answerLootChangeRequest(third, false);
                return party.getDistributionType();
            });

            assertThat(actual).isEqualTo(PartyDistributionType.FINDERS_KEEPERS);
        }
    }
}
