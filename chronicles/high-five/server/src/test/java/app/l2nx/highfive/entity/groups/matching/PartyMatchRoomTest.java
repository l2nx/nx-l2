/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.groups.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Player;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class PartyMatchRoomTest {
    private final Player owner = player("Owner");
    private final Player second = player("Second");
    private final Player third = player("Third");

    private static Player player(String name) {
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private PartyMatchRoom roomOfThree() {
        return WorldLoopExtension.callInPass(() -> {
            final PartyMatchRoom room = new PartyMatchRoom(7, "Title", 0, 1, 85, 9, owner);
            room.addMember(second);
            room.addMember(third);
            return room;
        });
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<PartyMatchRoom> call) {
            final PartyMatchRoom room = roomOfThree();

            assertThatThrownBy(() -> call.accept(room))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<PartyMatchRoom>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getId (accessor)", PartyMatchRoom::getId),
                    Named.of("getTitle (accessor)", PartyMatchRoom::getTitle),
                    Named.of("getLootType (accessor)", PartyMatchRoom::getLootType),
                    Named.of("collectMembers", r -> r.collectMembers(new ArrayList<>())),
                    Named.of("getPartyMembers (accessor)", PartyMatchRoom::getPartyMembers),
                    Named.of("containsMember", r -> r.containsMember(null)),
                    Named.of("addMember", r -> r.addMember(null)),
                    Named.of("deleteMember", r -> r.deleteMember(null)),
                    Named.of("changeLeader", r -> r.changeLeader(null)),
                    Named.of("getMembers", PartyMatchRoom::getMembers),
                    Named.of("getOwner", PartyMatchRoom::getOwner),
                    Named.of("changeSettings", r -> r.changeSettings(null, 1, 1, 1, 0, "Title")),
                    Named.of("isAbandoned", PartyMatchRoom::isAbandoned));
        }
    }

    @Nested
    class GetPartyMembers {
        @Test
        void getPartyMembers_shouldBeAReadOnlySnapshotInJoiningOrder() {
            final PartyMatchRoom room = roomOfThree();

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                final List<Player> snapshot = room.getPartyMembers();
                room.deleteMember(second);
                return snapshot;
            });

            assertThat(actual).containsExactly(owner, second, third);
            assertThatThrownBy(() -> actual.add(owner)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void containsMember_shouldAnswerFromTheLiveList() {
            final PartyMatchRoom room = roomOfThree();

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                final boolean before = room.containsMember(third);
                room.deleteMember(third);
                return List.of(before, room.containsMember(third));
            });

            assertThat(actual).containsExactly(true, false);
        }
    }

    @Nested
    class IsAbandoned {
        @Test
        void isAbandoned_shouldBeFalse_whenTheOwnerIsOnlineInThisRoom() {
            final PartyMatchRoom room = roomOfThree();
            when(owner.isOnline()).thenReturn(true);
            when(owner.getPartyRoom()).thenReturn(7);

            final boolean actual = WorldLoopExtension.callInPass(room::isAbandoned);

            assertThat(actual).isFalse();
        }

        @ParameterizedTest(name = "online={0}, room of the owner={1}")
        @CsvSource({"false, 7", "true, 0", "true, 8"})
        void isAbandoned_shouldBeTrue_whenTheOwnerIsGoneOrInAnotherRoom(boolean online, int ownerRoom) {
            final PartyMatchRoom room = roomOfThree();
            when(owner.isOnline()).thenReturn(online);
            when(owner.getPartyRoom()).thenReturn(ownerRoom);

            final boolean actual = WorldLoopExtension.callInPass(room::isAbandoned);

            assertThat(actual).isTrue();
        }
    }

    @Nested
    class ChangeSettings {
        @Test
        void changeSettings_shouldApplyTheSettings_whenTheOwnerEdits() {
            final PartyMatchRoom room = roomOfThree();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> List.of(
                    room.changeSettings(owner, 5, 40, 60, 2, "Other"),
                    room.getMaxMembers(),
                    room.getMinLevel(),
                    room.getMaxLevel(),
                    room.getLootType(),
                    room.getTitle()));

            assertThat(actual).containsExactly(true, 5, 40, 60, 2, "Other");
        }

        @Test
        void changeSettings_shouldChangeNothing_whenAMemberWhoIsNotTheOwnerEdits() {
            final PartyMatchRoom room = roomOfThree();

            final List<Object> actual = WorldLoopExtension.callInPass(() ->
                    List.of(room.changeSettings(second, 5, 40, 60, 2, "Other"), room.getMaxMembers(), room.getTitle()));

            assertThat(actual).containsExactly(false, 9, "Title");
        }
    }

    @Nested
    class ChangeLeader {
        @Test
        void changeLeader_shouldPutTheNewLeaderFirstAndTheOldOneLast() {
            final PartyMatchRoom room = roomOfThree();

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                room.changeLeader(third);
                return room.getPartyMembers();
            });

            assertThat(actual).containsExactly(third, second, owner);
        }
    }

    @Nested
    class DeleteMember {
        @Test
        void deleteMember_shouldKeepTheOrderOfTheOthers() {
            final PartyMatchRoom room = roomOfThree();

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                room.deleteMember(second);
                return room.getPartyMembers();
            });

            assertThat(actual).containsExactly(owner, third);
        }

        @Test
        void deleteMember_shouldHandTheRoomToTheNextMember_whenTheOwnerLeaves() {
            final PartyMatchRoom room = roomOfThree();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                room.deleteMember(owner);
                return List.of(room.getOwner(), room.getPartyMembers());
            });

            assertThat(actual).containsExactly(second, List.of(second, third));
        }
    }
}
