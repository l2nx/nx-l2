/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.groups.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExClosePartyRoom;
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
class PartyMatchRoomListTest {
    private final PartyMatchRoomList list = new PartyMatchRoomList();
    private final Player firstOwner = mock(Player.class);
    private final Player secondOwner = mock(Player.class);
    private final Player member = mock(Player.class);

    private record Rooms(PartyMatchRoom first, PartyMatchRoom second) {}

    private Rooms twoRooms() {
        return WorldLoopExtension.callInPass(() -> {
            final PartyMatchRoom first = new PartyMatchRoom(list.getMaxId(), "First", 0, 1, 85, 9, firstOwner);
            list.addPartyMatchRoom(first.getId(), first);
            final PartyMatchRoom second = new PartyMatchRoom(list.getMaxId(), "Second", 0, 1, 85, 9, secondOwner);
            list.addPartyMatchRoom(second.getId(), second);
            second.addMember(member);
            return new Rooms(first, second);
        });
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<PartyMatchRoomList> call) {
            assertThatThrownBy(() -> call.accept(list))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<PartyMatchRoomList>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getInstance (static)", _ -> PartyMatchRoomList.getInstance()),
                    Named.of("getMaxId (accessor)", PartyMatchRoomList::getMaxId),
                    Named.of("addPartyMatchRoom (former synchronized)", l -> l.addPartyMatchRoom(1, null)),
                    Named.of("deleteRoom", l -> l.deleteRoom(1)),
                    Named.of("dismissRoom", l -> l.dismissRoom(null, 1)),
                    Named.of("getRoom", l -> l.getRoom(1)),
                    Named.of("getRooms", PartyMatchRoomList::getRooms),
                    Named.of("getPartyMatchRoomCount", PartyMatchRoomList::getPartyMatchRoomCount),
                    Named.of("getPlayerRoom", l -> l.getPlayerRoom(null)),
                    Named.of("getPlayerRoomId", l -> l.getPlayerRoomId(null)),
                    Named.of("deleteAbandonedRooms", PartyMatchRoomList::deleteAbandonedRooms));
        }
    }

    @Nested
    class GetRooms {
        @Test
        void getRooms_shouldBeAReadOnlySnapshotInCreationOrder() {
            final Rooms rooms = twoRooms();

            final List<PartyMatchRoom> actual = WorldLoopExtension.callInPass(() -> {
                final List<PartyMatchRoom> snapshot = list.getRooms();
                list.deleteRoom(rooms.first().getId());
                return snapshot;
            });

            assertThat(actual).containsExactly(rooms.first(), rooms.second());
            assertThatThrownBy(() -> actual.add(rooms.first())).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void addPartyMatchRoom_shouldAdvanceTheNextId() {
            twoRooms();

            final int actual = WorldLoopExtension.callInPass(list::getMaxId);

            assertThat(actual).isEqualTo(3);
        }
    }

    @Nested
    class GetPlayerRoom {
        @Test
        void getPlayerRoom_shouldFindTheRoomOfAMember() {
            final Rooms rooms = twoRooms();
            final Player stranger = mock(Player.class);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> List.of(
                    list.getPlayerRoom(member),
                    list.getPlayerRoomId(member),
                    list.getPlayerRoomId(firstOwner),
                    list.getPlayerRoomId(stranger)));

            assertThat(actual).containsExactly(rooms.second(), 2, 1, -1);
        }
    }

    @Nested
    class DeleteRoom {
        @Test
        void deleteRoom_shouldTakeEveryMemberOutOfTheRoom() {
            final Rooms rooms = twoRooms();
            when(secondOwner.getPartyRoom()).thenReturn(2);
            when(member.getPartyRoom()).thenReturn(2);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                list.deleteRoom(rooms.second().getId());
                return List.of(
                        list.getPartyMatchRoomCount(),
                        list.getRoom(rooms.second().getId()) == null);
            });

            assertThat(actual).containsExactly(1, true);
            verify(secondOwner).setPartyRoom(0);
            verify(member).setPartyRoom(0);
        }

        @Test
        void deleteAbandonedRooms_shouldDeleteOnlyTheRoomsWhoseOwnerIsGone() {
            final Rooms rooms = twoRooms();
            when(firstOwner.isOnline()).thenReturn(true);
            when(firstOwner.getPartyRoom()).thenReturn(1);
            when(member.getPartyRoom()).thenReturn(2);

            final List<PartyMatchRoom> actual = WorldLoopExtension.callInPass(() -> {
                list.deleteAbandonedRooms();
                return list.getRooms();
            });

            assertThat(actual).containsExactly(rooms.first());
            verify(member).setPartyRoom(0);
        }

        @Test
        void deleteAbandonedRooms_shouldLeaveTheOwner_whenHeMovedToAnotherRoom() {
            final Rooms rooms = twoRooms();
            when(firstOwner.isOnline()).thenReturn(true);
            when(firstOwner.getPartyRoom()).thenReturn(1);
            when(secondOwner.isOnline()).thenReturn(true);
            when(secondOwner.getPartyRoom()).thenReturn(1);
            when(member.getPartyRoom()).thenReturn(2);

            WorldLoopExtension.runInPass(list::deleteAbandonedRooms);

            verify(secondOwner, never()).setPartyRoom(0);
            verify(secondOwner, never()).sendPacket(isA(ExClosePartyRoom.class));
            verify(member).setPartyRoom(0);
            verify(member).sendPacket(isA(ExClosePartyRoom.class));
        }

        @Test
        void dismissRoom_shouldDeleteTheRoom_whenTheOwnerAsks() {
            final Rooms rooms = twoRooms();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> List.of(
                    list.dismissRoom(firstOwner, rooms.first().getId()),
                    list.getRoom(rooms.first().getId()) == null));

            assertThat(actual).containsExactly(true, true);
        }

        @Test
        void dismissRoom_shouldKeepTheRoom_whenAMemberWhoIsNotTheOwnerAsks() {
            final Rooms rooms = twoRooms();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> List.of(
                    list.dismissRoom(member, rooms.second().getId()),
                    list.dismissRoom(firstOwner, rooms.second().getId()),
                    list.getPartyMatchRoomCount()));

            assertThat(actual).containsExactly(false, false, 2);
            verify(member, never()).setPartyRoom(0);
        }

        @Test
        void deleteRoom_shouldDoNothing_whenTheRoomIsUnknown() {
            twoRooms();

            final int actual = WorldLoopExtension.callInPass(() -> {
                list.deleteRoom(99);
                return list.getPartyMatchRoomCount();
            });

            assertThat(actual).isEqualTo(2);
        }
    }
}
