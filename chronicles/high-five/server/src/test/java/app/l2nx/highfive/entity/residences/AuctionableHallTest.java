/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.residences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.residence.ClanHallFunctionRepository;
import app.l2nx.highfive.repository.residence.ClanHallStateRepository;
import app.l2nx.highfive.repository.residence.model.ClanHallState;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import app.l2nx.highfive.util.StatSet;
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
class AuctionableHallTest {
    private static final int HALL = 22;
    private static final int UNLOADED_CLAN = 268_435_456;
    private static final long PAID_UNTIL = ClockBridgeExtension.WALL_START.toEpochMilli() + 86_400_000L;

    private final ClanHallStateRepository states = mock(ClanHallStateRepository.class);
    private final ClanHallFunctionRepository functions = mock(ClanHallFunctionRepository.class);

    private MockedStatic<ClanTable> clanTable;

    @BeforeEach
    void bindBridges() {
        final ClanTable clans = mock(ClanTable.class);
        when(clans.getClan(anyInt())).thenReturn(null);
        clanTable = mockStatic(ClanTable.class);
        clanTable.when(ClanTable::getInstance).thenReturn(clans);
        new ClanHallTable(states, functions);
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
        set.set("paidUntil", PAID_UNTIL);
        set.set("grade", 1);
        set.set("paid", true);
        return set;
    }

    @Nested
    class OwnershipCheck {
        private final AuctionableHall hall = WorldLoopExtension.callInPass(() -> new AuctionableHall(hallOwnedBy(0)));

        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<AuctionableHall> call) {
            assertThatThrownBy(() -> call.accept(hall))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<AuctionableHall>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getPaidUntil (accessor)", AuctionableHall::getPaidUntil),
                    Named.of("getGrade", AuctionableHall::getGrade),
                    Named.of("getLease", AuctionableHall::getLease),
                    Named.of("getPaid", AuctionableHall::getPaid),
                    Named.of("free", AuctionableHall::free),
                    Named.of("setOwner", h -> h.setOwner(null)),
                    Named.of("updateDb", AuctionableHall::updateDb));
        }
    }

    @Nested
    class SetOwner {
        @Test
        void setOwner_shouldKeepOnePendingFee_whenTheHallChangesOwner(Loop world) {
            final Clan first = mock(Clan.class);
            when(first.getId()).thenReturn(268_435_457);
            final Clan second = mock(Clan.class);
            when(second.getId()).thenReturn(268_435_458);

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                final AuctionableHall hall = new AuctionableHall(hallOwnedBy(0));
                final int base = world.timers(TimeBase.GAME).size();
                hall.setOwner(first);
                final int firstOwner = world.timers(TimeBase.GAME).size() - base;
                hall.free();
                hall.setOwner(second);
                return List.of(firstOwner, world.timers(TimeBase.GAME).size() - base, hall.getOwnerId());
            });

            assertThat(actual).containsExactly(1, 1, 268_435_458);
        }
    }

    @Nested
    class ReleaseUnloadedOwner {
        @Test
        void construct_shouldNotPersist_whenOwnerClanIsNotLoaded() {
            WorldLoopExtension.runInPass(() -> new AuctionableHall(hallOwnedBy(UNLOADED_CLAN)));

            verifyNoInteractions(states);
        }

        @Test
        void releaseUnloadedOwner_shouldFreeHallAndPersistNoOwner_whenOwnerClanIsNotLoaded() {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final AuctionableHall hall = new AuctionableHall(hallOwnedBy(UNLOADED_CLAN));
                hall.releaseUnloadedOwner();
                return List.of(hall.getOwnerId(), hall.isFree(), hall.getPaidUntil(), hall.getPaid());
            });

            assertThat(actual).containsExactly(0, true, 0L, false);
            verify(states).save(ClanHallState.builder().clanHallId(HALL).build());
        }

        @Test
        void releaseUnloadedOwner_shouldDeleteStoredFunctions_whenOwnerClanIsNotLoaded() {
            when(functions.findByClanHall(HALL))
                    .thenReturn(List.of(ResidenceFunction.builder()
                            .type(ClanHall.FUNC_TELEPORT)
                            .level(1)
                            .build()));
            WorldLoopExtension.runInPass(() -> new AuctionableHall(hallOwnedBy(UNLOADED_CLAN)).releaseUnloadedOwner());

            verify(functions).delete(HALL, ClanHall.FUNC_TELEPORT);
        }

        @Test
        void releaseUnloadedOwner_shouldDoNothing_whenHallHasNoOwner() {
            final long actual = WorldLoopExtension.callInPass(() -> {
                final AuctionableHall hall = new AuctionableHall(hallOwnedBy(0));
                hall.releaseUnloadedOwner();
                return hall.getPaidUntil();
            });

            assertThat(actual).isEqualTo(PAID_UNTIL);
            verifyNoInteractions(states, functions);
        }
    }
}
