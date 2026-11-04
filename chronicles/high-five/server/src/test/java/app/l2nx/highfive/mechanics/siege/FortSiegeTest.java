/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.siege;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class FortSiegeTest {
    private static final int FORT_ID = 101;
    private static final int CLAN_ID = 268_435_457;
    private static final long FEE = 250_000;

    private final Fort fort = mock(Fort.class);
    private final FortSiegeManager fortSieges = mock(FortSiegeManager.class);
    private final Player player = mock(Player.class);
    private final List<MockedStatic<?>> bridges = new ArrayList<>();

    private FortSiege siege;

    @BeforeEach
    void loadSiege() {
        bind(FortSiegeManager.class, FortSiegeManager::getInstance, fortSieges);
        bind(FortManager.class, FortManager::getInstance, mock(FortManager.class));
        bind(TerritoryWarManager.class, TerritoryWarManager::getInstance, mock(TerritoryWarManager.class));
        final Calendar siegeDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        siegeDate.setTimeInMillis(
                ClockBridgeExtension.WALL_START.plus(Duration.ofHours(2)).toEpochMilli());
        when(fort.getSiegeDate()).thenReturn(siegeDate);
        when(fort.getResidenceId()).thenReturn(FORT_ID);
        when(fortSieges.getAttackerMaxClans()).thenReturn(500);
        final Clan clan = mock(Clan.class);
        when(clan.getId()).thenReturn(CLAN_ID);
        when(player.getClan()).thenReturn(clan);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getAdena()).thenReturn(FEE);
        when(player.getInventory()).thenReturn(inventory);
        when(player.reduceAdena(ItemProcessType.FEE, FEE, null, true)).thenReturn(true);
        siege = WorldLoopExtension.callInPass(() -> new FortSiege(fort));
        when(fort.getSiege()).thenReturn(siege);
    }

    @AfterEach
    void releaseBridges() {
        bridges.forEach(MockedStatic::close);
    }

    private <T> void bind(Class<T> type, MockedStatic.Verification getInstance, T instance) {
        final MockedStatic<T> bridge = mockStatic(type);
        bridge.when(getInstance).thenReturn(instance);
        bridges.add(bridge);
    }

    @Nested
    class AddAttacker {
        @Test
        void addAttacker_shouldRefundAndReportTheFailure_whenThePaidRegistrationIsNotSaved() {
            when(fortSieges.saveSiegeClan(FORT_ID, CLAN_ID)).thenReturn(false);

            final int actual = WorldLoopExtension.callInPass(() -> siege.addAttacker(player, true));

            assertThat(actual).isEqualTo(3);
            verify(player).addAdena(ItemProcessType.REFUND, FEE, null, true);
            assertThat(siege.getAttackerClan(CLAN_ID)).isNull();
        }

        @Test
        void addAttacker_shouldReportTheFailure_whenTheUnpaidRegistrationIsNotSaved() {
            when(fortSieges.saveSiegeClan(FORT_ID, CLAN_ID)).thenReturn(false);

            final int actual = WorldLoopExtension.callInPass(() -> siege.addAttacker(player, false));

            assertThat(actual).isEqualTo(3);
            verify(player, never()).reduceAdena(any(), anyLong(), any(), anyBoolean());
            verify(player, never()).addAdena(any(), anyLong(), any(), anyBoolean());
        }

        @Test
        void addAttacker_shouldKeepTheFee_whenTheRegistrationIsSaved() {
            when(fortSieges.saveSiegeClan(FORT_ID, CLAN_ID)).thenReturn(true);

            final int actual = WorldLoopExtension.callInPass(() -> siege.addAttacker(player, true));

            assertThat(actual).isEqualTo(4);
            verify(player).reduceAdena(ItemProcessType.FEE, FEE, null, true);
            verify(player, never()).addAdena(any(), anyLong(), any(), anyBoolean());
            assertThat(siege.getAttackerClan(CLAN_ID)).isNotNull();
        }
    }
}
