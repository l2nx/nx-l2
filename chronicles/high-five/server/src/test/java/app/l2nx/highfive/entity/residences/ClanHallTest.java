/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.residences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.residence.ClanHallFunctionRepository;
import app.l2nx.highfive.repository.residence.ClanHallStateRepository;
import app.l2nx.highfive.util.StatSet;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class ClanHallTest {
    private static final int HALL = 22;
    private static final int OWNER = 268_435_457;
    private static final long RATE = 86_400_000L;

    private final ClanHallFunctionRepository functions = mock(ClanHallFunctionRepository.class);
    private final Clan owner = mock(Clan.class);
    private final Player member = mock(Player.class);

    private MockedStatic<ClanTable> clanTable;

    @BeforeEach
    void bindBridges() {
        final ClanTable clans = mock(ClanTable.class);
        when(clans.getClan(OWNER)).thenReturn(owner);
        clanTable = mockStatic(ClanTable.class);
        clanTable.when(ClanTable::getInstance).thenReturn(clans);
        new ClanHallTable(mock(ClanHallStateRepository.class), functions);
        when(member.destroyItemByItemId(any(), anyInt(), anyLong(), any(), anyBoolean()))
                .thenReturn(true);
    }

    @AfterEach
    void releaseClanTable() {
        clanTable.close();
    }

    private static StatSet hallOwnedBy(int ownerId) {
        final StatSet set = new StatSet();
        set.set("id", HALL);
        set.set("name", "Moonstone Hall");
        set.set("ownerId", ownerId);
        set.set("lease", 100_000);
        set.set("desc", "Auctionable hall");
        set.set("location", "Gludio");
        set.set("paidUntil", ClockBridgeExtension.WALL_START.toEpochMilli() + RATE);
        set.set("grade", 1);
        set.set("paid", true);
        return set;
    }

    private static Door door(int doorId) {
        final Door door = mock(Door.class);
        when(door.getId()).thenReturn(doorId);
        return door;
    }

    @Nested
    class OwnershipCheck {
        private final ClanHall hall = WorldLoopExtension.callInPass(() -> new AuctionableHall(hallOwnedBy(0)));

        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ClanHall> call) {
            assertThatThrownBy(() -> call.accept(hall))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ClanHall>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getId (accessor)", ClanHall::getId),
                    Named.of("getName", ClanHall::getName),
                    Named.of("getOwnerId", ClanHall::getOwnerId),
                    Named.of("getDesc", ClanHall::getDesc),
                    Named.of("getLocation", ClanHall::getLocation),
                    Named.of("getZone", ClanHall::getZone),
                    Named.of("setZone", h -> h.setZone(null)),
                    Named.of("isFree", ClanHall::isFree),
                    Named.of("getDoors", ClanHall::getDoors),
                    Named.of("addDoor", h -> h.addDoor(null)),
                    Named.of("getDoor", h -> h.getDoor(1)),
                    Named.of("getFunction", h -> h.getFunction(ClanHall.FUNC_TELEPORT)),
                    Named.of("checkIfInZone", h -> h.checkIfInZone(0, 0, 0)),
                    Named.of("free", ClanHall::free),
                    Named.of("setOwner", h -> h.setOwner(null)),
                    Named.of("openCloseDoor (player)", h -> h.openCloseDoor(null, 1, true)),
                    Named.of("openCloseDoor (id)", h -> h.openCloseDoor(1, true)),
                    Named.of("openCloseDoor (door)", h -> h.openCloseDoor(null, true)),
                    Named.of("openCloseDoors (player)", h -> h.openCloseDoors(null, true)),
                    Named.of("openCloseDoors", h -> h.openCloseDoors(true)),
                    Named.of("banishForeigners", ClanHall::banishForeigners),
                    Named.of("removeFunction", h -> h.removeFunction(ClanHall.FUNC_TELEPORT)),
                    Named.of("updateFunctions", h -> h.updateFunctions(null, 1, 1, 0, RATE, true)),
                    Named.of("getGrade", ClanHall::getGrade),
                    Named.of("getPaidUntil", ClanHall::getPaidUntil),
                    Named.of("getLease", ClanHall::getLease),
                    Named.of("isSiegableHall", ClanHall::isSiegableHall),
                    Named.of("releaseUnloadedOwner", ClanHall::releaseUnloadedOwner),
                    Named.of("updateDb (override)", ClanHall::updateDb));
        }
    }

    @Nested
    class Doors {
        @Test
        void getDoors_shouldBeAReadOnlySnapshotInRegistrationOrder() {
            final Door first = door(10);
            final Door second = door(11);

            final List<Door> actual = WorldLoopExtension.callInPass(() -> {
                final ClanHall hall = new AuctionableHall(hallOwnedBy(0));
                hall.addDoor(first);
                hall.addDoor(second);
                final List<Door> snapshot = hall.getDoors();
                hall.addDoor(door(12));
                return snapshot;
            });

            assertThat(actual).containsExactly(first, second);
            assertThatThrownBy(() -> actual.add(first)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getDoor_shouldFindTheDoorById() {
            final Door wanted = door(11);

            final List<Door> actual = WorldLoopExtension.callInPass(() -> {
                final ClanHall hall = new AuctionableHall(hallOwnedBy(0));
                hall.addDoor(door(10));
                hall.addDoor(wanted);
                return Arrays.asList(hall.getDoor(11), hall.getDoor(12), hall.getDoor(0));
            });

            assertThat(actual).containsExactly(wanted, null, null);
        }

        @Test
        void openCloseDoors_shouldOpenEveryDoor_whenOpeningOneRegistersAnother() {
            final Door first = door(10);
            final Door second = door(11);
            final Door late = door(12);

            WorldLoopExtension.runInPass(() -> {
                final ClanHall hall = new AuctionableHall(hallOwnedBy(0));
                doAnswer(_ -> {
                            hall.addDoor(late);
                            return null;
                        })
                        .when(first)
                        .openMe();
                hall.addDoor(first);
                hall.addDoor(second);
                hall.openCloseDoors(true);
            });

            verify(first).openMe();
            verify(second).openMe();
        }
    }

    @Nested
    class Functions {
        @Test
        void free_shouldRemoveEveryFunctionAndItsFee(Loop world) {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final ClanHall hall = new AuctionableHall(hallOwnedBy(OWNER));
                hall.updateFunctions(member, ClanHall.FUNC_TELEPORT, 1, 0, RATE, true);
                hall.updateFunctions(member, ClanHall.FUNC_RESTORE_HP, 1, 0, RATE, true);
                final int before = world.timers(TimeBase.GAME).size();
                hall.free();
                return Arrays.asList(
                        before - world.timers(TimeBase.GAME).size(),
                        hall.getFunction(ClanHall.FUNC_TELEPORT),
                        hall.getFunction(ClanHall.FUNC_RESTORE_HP),
                        hall.isFree());
            });

            assertThat(actual).containsExactly(2, null, null, true);
            verify(functions).delete(HALL, ClanHall.FUNC_TELEPORT);
            verify(functions).delete(HALL, ClanHall.FUNC_RESTORE_HP);
        }
    }
}
