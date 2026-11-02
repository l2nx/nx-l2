/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.siege.clanhalls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.residence.ClanHallFunctionRepository;
import app.l2nx.highfive.repository.residence.ClanHallStateRepository;
import app.l2nx.highfive.repository.residence.SiegableClanHallStateRepository;
import app.l2nx.highfive.repository.residence.model.SiegableClanHallState;
import app.l2nx.highfive.repository.siege.ClanHallSiegeAttackerRepository;
import app.l2nx.highfive.repository.siege.FlagWarAttackerRepository;
import app.l2nx.highfive.repository.siege.RainbowSpringsAttackerRepository;
import app.l2nx.highfive.util.StatSet;
import java.time.Instant;
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
class SiegableHallTest {
    private static final int HALL = 21;
    private static final int LOADED_CLAN = 268_435_457;
    private static final int UNLOADED_CLAN = 268_435_456;
    private static final Instant NEXT_SIEGE = ClockBridgeExtension.WALL_START.plusSeconds(7 * 86_400);

    private final SiegableClanHallStateRepository states = mock(SiegableClanHallStateRepository.class);
    private final Clan loadedClan = mock(Clan.class);

    private MockedStatic<ClanTable> clanTable;

    @BeforeEach
    void bindBridges() {
        final ClanTable clans = mock(ClanTable.class);
        when(clans.getClan(LOADED_CLAN)).thenReturn(loadedClan);
        clanTable = mockStatic(ClanTable.class);
        clanTable.when(ClanTable::getInstance).thenReturn(clans);
        new ClanHallTable(mock(ClanHallStateRepository.class), mock(ClanHallFunctionRepository.class));
        new CHSiegeManager(
                states,
                mock(ClanHallSiegeAttackerRepository.class),
                mock(FlagWarAttackerRepository.class),
                mock(RainbowSpringsAttackerRepository.class));
    }

    @AfterEach
    void releaseClanTable() {
        clanTable.close();
    }

    private static StatSet hallOwnedBy(int ownerId) {
        final StatSet set = new StatSet();
        set.set("id", HALL);
        set.set("name", "Fortress of Resistance");
        set.set("ownerId", ownerId);
        set.set("desc", "Siegable hall");
        set.set("location", "Dion");
        set.set("nextSiege", NEXT_SIEGE.toEpochMilli());
        set.set("siegeLength", 3_600_000L);
        set.set("scheduleDays", 14);
        set.set("scheduleMonths", 0);
        set.set("scheduleYears", 0);
        set.set("siegeHour", 20);
        set.set("siegeMinute", 0);
        return set;
    }

    @Nested
    class OwnershipCheck {
        private SiegableHall hall;

        // After the outer stubs: the hall reads its owner clan from them.
        @BeforeEach
        void createHall() {
            hall = WorldLoopExtension.callInPass(() -> new SiegableHall(hallOwnedBy(0)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<SiegableHall> call) {
            assertThatThrownBy(() -> call.accept(hall))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<SiegableHall>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getSiegeDate (accessor)", SiegableHall::getSiegeDate),
                    Named.of("getSiegeLength", SiegableHall::getSiegeLength),
                    Named.of("getSiegeStatus", SiegableHall::getSiegeStatus),
                    Named.of("getSiegeZone", SiegableHall::getSiegeZone),
                    Named.of("setSiegeZone", h -> h.setSiegeZone(null)),
                    Named.of("getSiege", SiegableHall::getSiege),
                    Named.of("setSiege", h -> h.setSiege(null)),
                    Named.of("spawnDoor", SiegableHall::spawnDoor),
                    Named.of("updateDb (override)", SiegableHall::updateDb),
                    Named.of("getNextSiegeTime", SiegableHall::getNextSiegeTime),
                    Named.of("setNextSiegeDate", h -> h.setNextSiegeDate(0L)),
                    Named.of("updateNextSiege", SiegableHall::updateNextSiege),
                    Named.of("addAttacker", h -> h.addAttacker(null)),
                    Named.of("removeAttacker", h -> h.removeAttacker(null)),
                    Named.of("isRegistered", h -> h.isRegistered(null)),
                    Named.of("isRegistering", SiegableHall::isRegistering),
                    Named.of("isInSiege", SiegableHall::isInSiege),
                    Named.of("isWaitingBattle", SiegableHall::isWaitingBattle),
                    Named.of("updateSiegeStatus", h -> h.updateSiegeStatus(null)),
                    Named.of("updateSiegeZone", h -> h.updateSiegeZone(true)),
                    Named.of("showSiegeInfo", h -> h.showSiegeInfo(null)),
                    Named.of("isSiegableHall (override)", SiegableHall::isSiegableHall),
                    Named.of("getZone (override)", SiegableHall::getZone));
        }
    }

    @Nested
    class SiegeDate {
        @Test
        void getSiegeDate_shouldHandOutACopy() {
            final long siegeAt = NEXT_SIEGE.plusSeconds(86_400).toEpochMilli();

            final long actual = WorldLoopExtension.callInPass(() -> {
                final SiegableHall hall = new SiegableHall(hallOwnedBy(0));
                hall.setNextSiegeDate(siegeAt);
                hall.getSiegeDate().setTimeInMillis(0);
                return hall.getSiegeDate().getTimeInMillis();
            });

            assertThat(actual).isEqualTo(siegeAt);
        }

        @Test
        void setNextSiegeDate_shouldReplaceTheDate() {
            final long siegeAt = NEXT_SIEGE.plusSeconds(86_400).toEpochMilli();

            final List<Long> actual = WorldLoopExtension.callInPass(() -> {
                final SiegableHall hall = new SiegableHall(hallOwnedBy(0));
                hall.setNextSiegeDate(siegeAt);
                return List.of(hall.getSiegeDate().getTimeInMillis(), hall.getNextSiegeTime());
            });

            assertThat(actual).containsExactly(siegeAt, siegeAt);
        }
    }

    @Nested
    class ReleaseUnloadedOwner {
        @Test
        void construct_shouldNotPersist_whenOwnerClanIsNotLoaded() {
            WorldLoopExtension.runInPass(() -> new SiegableHall(hallOwnedBy(UNLOADED_CLAN)));

            verify(states, never()).save(any());
        }

        @Test
        void releaseUnloadedOwner_shouldFreeHallAndPersistNoOwner_whenOwnerClanIsNotLoaded() {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final SiegableHall hall = new SiegableHall(hallOwnedBy(UNLOADED_CLAN));
                hall.releaseUnloadedOwner();
                return List.of(hall.getOwnerId(), hall.isFree());
            });

            assertThat(actual).containsExactly(0, true);
            verify(states)
                    .save(SiegableClanHallState.builder()
                            .clanHallId(HALL)
                            .nextSiege(NEXT_SIEGE)
                            .build());
        }

        @Test
        void releaseUnloadedOwner_shouldKeepOwner_whenOwnerClanIsLoaded() {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final SiegableHall hall = new SiegableHall(hallOwnedBy(LOADED_CLAN));
                hall.releaseUnloadedOwner();
                return List.of(hall.getOwnerId(), hall.isFree());
            });

            assertThat(actual).containsExactly(LOADED_CLAN, false);
            verify(loadedClan).setHideoutId(HALL);
            verify(states, never()).save(any());
        }
    }
}
