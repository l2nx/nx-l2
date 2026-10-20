/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.residences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.repository.residence.ClanHallFunctionRepository;
import app.l2nx.highfive.repository.residence.ClanHallStateRepository;
import app.l2nx.highfive.repository.residence.model.ClanHallState;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import app.l2nx.highfive.util.StatSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

@ExtendWith(ClockBridgeExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
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
    class ReleaseUnloadedOwner {
        @Test
        void construct_shouldNotPersist_whenOwnerClanIsNotLoaded() {
            new AuctionableHall(hallOwnedBy(UNLOADED_CLAN));

            verifyNoInteractions(states);
        }

        @Test
        void releaseUnloadedOwner_shouldFreeHallAndPersistNoOwner_whenOwnerClanIsNotLoaded() {
            final AuctionableHall hall = new AuctionableHall(hallOwnedBy(UNLOADED_CLAN));

            hall.releaseUnloadedOwner();

            assertThat(hall.getOwnerId()).isZero();
            assertThat(hall.isFree()).isTrue();
            assertThat(hall.getPaidUntil()).isZero();
            assertThat(hall.getPaid()).isFalse();
            verify(states).save(ClanHallState.builder().clanHallId(HALL).build());
        }

        @Test
        void releaseUnloadedOwner_shouldDeleteStoredFunctions_whenOwnerClanIsNotLoaded() {
            when(functions.findByClanHall(HALL))
                    .thenReturn(List.of(ResidenceFunction.builder()
                            .type(ClanHall.FUNC_TELEPORT)
                            .level(1)
                            .build()));
            final AuctionableHall hall = new AuctionableHall(hallOwnedBy(UNLOADED_CLAN));

            hall.releaseUnloadedOwner();

            verify(functions).delete(HALL, ClanHall.FUNC_TELEPORT);
        }

        @Test
        void releaseUnloadedOwner_shouldDoNothing_whenHallHasNoOwner() {
            final AuctionableHall hall = new AuctionableHall(hallOwnedBy(0));

            hall.releaseUnloadedOwner();

            assertThat(hall.getPaidUntil()).isEqualTo(PAID_UNTIL);
            verifyNoInteractions(states, functions);
        }
    }
}
