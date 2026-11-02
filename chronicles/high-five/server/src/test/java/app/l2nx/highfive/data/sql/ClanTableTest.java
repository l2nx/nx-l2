/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.communitybbs.Manager.ForumsBBSManager;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.residences.AuctionableHall;
import app.l2nx.highfive.entity.residences.ClanHallAuction;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.ClanHallAuctionManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.FortSiege;
import app.l2nx.highfive.mechanics.siege.Siege;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.clan.ClanNoticeRepository;
import app.l2nx.highfive.repository.clan.ClanRankPrivilegeRepository;
import app.l2nx.highfive.repository.clan.ClanRepository;
import app.l2nx.highfive.repository.clan.ClanSkillRepository;
import app.l2nx.highfive.repository.clan.ClanSubPledgeRepository;
import app.l2nx.highfive.repository.clan.ClanWarRepository;
import app.l2nx.highfive.repository.residence.CastleStateRepository;
import app.l2nx.highfive.repository.residence.ClanHallFunctionRepository;
import app.l2nx.highfive.repository.residence.ClanHallStateRepository;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class ClanTableTest {
    private static final int CLAN_ID = 268_435_456;
    private static final int OTHER_CLAN_ID = 268_435_460;
    private static final int CASTLE = 3;
    private static final int FORT = 101;
    private static final int HALL = 22;

    private final ClanRepository clanRepository = mock(ClanRepository.class);
    private final CastleManager castles = mock(CastleManager.class);
    private final FortManager forts = mock(FortManager.class);
    private final CHSiegeManager siegableHalls = mock(CHSiegeManager.class);
    private final ClanHallAuctionManager auctions = mock(ClanHallAuctionManager.class);
    private final SiegeManager castleSieges = mock(SiegeManager.class);
    private final FortSiegeManager fortSieges = mock(FortSiegeManager.class);
    private final ClanHallTable halls =
            new ClanHallTable(mock(ClanHallStateRepository.class), mock(ClanHallFunctionRepository.class));
    private final ClanTable table = new ClanTable(
            clanRepository,
            mock(ClanNoticeRepository.class),
            mock(ClanRankPrivilegeRepository.class),
            mock(ClanSkillRepository.class),
            mock(ClanSubPledgeRepository.class),
            mock(ClanWarRepository.class),
            mock(CastleStateRepository.class),
            mock(CharacterRepository.class),
            mock(TransactionTemplate.class));

    private final List<MockedStatic<?>> bridges = new ArrayList<>();
    private Clan clan;

    @BeforeEach
    void bindBridges() {
        bind(ClanTable.class, ClanTable::getInstance, table);
        bind(ClanHallTable.class, ClanHallTable::getInstance, halls);
        bind(CastleManager.class, CastleManager::getInstance, castles);
        bind(FortManager.class, FortManager::getInstance, forts);
        bind(CHSiegeManager.class, CHSiegeManager::getInstance, siegableHalls);
        bind(ClanHallAuctionManager.class, ClanHallAuctionManager::getInstance, auctions);
        bind(SiegeManager.class, SiegeManager::getInstance, castleSieges);
        bind(FortSiegeManager.class, FortSiegeManager::getInstance, fortSieges);
        bind(IdManager.class, IdManager::getInstance, mock(IdManager.class));
        bind(ItemManager.class, ItemManager::getInstance, mock(ItemManager.class));
        bind(ForumsBBSManager.class, ForumsBBSManager::getInstance, mock(ForumsBBSManager.class));

        when(clanRepository.findAllClanIds()).thenReturn(List.of(CLAN_ID, OTHER_CLAN_ID));
        clan = WorldLoopExtension.callInPass(() -> {
            table.load();
            return table.getClan(CLAN_ID);
        });
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

    private AuctionableHall hallOwnedByClan() {
        final StatSet set = new StatSet();
        set.set("id", HALL);
        set.set("name", "Moonstone Hall");
        set.set("ownerId", CLAN_ID);
        set.set("lease", 100_000);
        set.set("desc", "Auctionable hall");
        set.set("location", "Gludio");
        set.set("paidUntil", ClockBridgeExtension.WALL_START.toEpochMilli() + 86_400_000L);
        set.set("grade", 1);
        set.set("paid", true);
        final AuctionableHall hall = WorldLoopExtension.callInPass(() -> new AuctionableHall(set));
        halls.getClanHalls().put(HALL, hall);
        return hall;
    }

    @Nested
    class DestroyClan {
        @Test
        void destroyClan_shouldEndWarsOfOtherClans_whenTheyFoughtTheClan() {
            final Clan other = WorldLoopExtension.callInPass(() -> {
                final Clan fought = table.getClan(OTHER_CLAN_ID);
                fought.setEnemyClan(CLAN_ID);
                fought.setAttackerClan(CLAN_ID);
                return fought;
            });

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                table.destroyClan(CLAN_ID);
                return List.of(other.isAtWarWith(CLAN_ID), other.isAtWarAttacker(CLAN_ID), other.isAtWar());
            });

            assertThat(actual).containsExactly(false, false, false);
        }

        @Test
        void destroyClan_shouldRemoveCastleOwnerAndTax_whenClanOwnsCastle() {
            final Castle castle = mock(Castle.class);
            WorldLoopExtension.runInPass(() -> clan.setCastleId(CASTLE));
            when(castles.getCastleByOwner(clan)).thenReturn(castle);

            WorldLoopExtension.runInPass(() -> table.destroyClan(CLAN_ID));

            final InOrder order = inOrder(castle, clanRepository);
            order.verify(castle).removeOwner(clan);
            order.verify(clanRepository).delete(CLAN_ID);
            verify(castle).setTaxPercent(0);
        }

        @Test
        void destroyClan_shouldFreeHallForNpcAuction_whenClanOwnsAuctionableHall() {
            final AuctionableHall hall = hallOwnedByClan();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                table.destroyClan(CLAN_ID);
                return List.of(hall.getOwnerId(), hall.isFree());
            });

            assertThat(actual).containsExactly(0, true);
            assertThat(halls.isFree(HALL)).isTrue();
            assertThat(halls.getClanHalls()).doesNotContainKey(HALL);
            verify(auctions).initNPC(HALL);
        }

        @Test
        void destroyClan_shouldCancelSaleAuctionBeforeClanRowIsDeleted_whenClanSellsItsHall() {
            hallOwnedByClan();
            final ClanHallAuction sale = mock(ClanHallAuction.class);
            when(auctions.getAuction(HALL)).thenReturn(sale);

            WorldLoopExtension.runInPass(() -> table.destroyClan(CLAN_ID));

            final InOrder order = inOrder(sale, auctions, clanRepository);
            order.verify(sale).cancelAuction();
            order.verify(auctions).initNPC(HALL);
            order.verify(clanRepository).delete(CLAN_ID);
        }

        @Test
        void destroyClan_shouldRemoveFortOwner_whenClanOwnsFort() {
            final Fort fort = mock(Fort.class);
            WorldLoopExtension.runInPass(() -> clan.setFortId(FORT));
            when(forts.getFortByOwner(clan)).thenReturn(fort);

            WorldLoopExtension.runInPass(() -> table.destroyClan(CLAN_ID));

            final InOrder order = inOrder(fort, clanRepository);
            order.verify(fort).removeOwner(true);
            order.verify(clanRepository).delete(CLAN_ID);
        }

        @Test
        void destroyClan_shouldFreeSiegableHall_whenClanOwnsIt() {
            final SiegableHall hall = mock(SiegableHall.class);
            when(hall.getOwnerId()).thenReturn(CLAN_ID);
            when(hall.isSiegableHall()).thenReturn(true);
            WorldLoopExtension.runInPass(() -> clan.setHideoutId(HALL));
            when(siegableHalls.getConquerableHalls()).thenReturn(Map.of(HALL, hall));

            WorldLoopExtension.runInPass(() -> table.destroyClan(CLAN_ID));

            final InOrder order = inOrder(hall, clanRepository);
            order.verify(hall).free();
            order.verify(clanRepository).delete(CLAN_ID);
        }

        @Test
        void destroyClan_shouldDropOwnerFromCastleSiege_afterCastleIsReleased() {
            final Castle castle = mock(Castle.class);
            final Siege siege = mock(Siege.class);
            WorldLoopExtension.runInPass(() -> clan.setCastleId(CASTLE));
            when(castles.getCastleByOwner(clan)).thenReturn(castle);
            when(castleSieges.getSieges()).thenReturn(List.of(siege));

            WorldLoopExtension.runInPass(() -> table.destroyClan(CLAN_ID));

            final InOrder order = inOrder(castle, siege);
            order.verify(castle).removeOwner(clan);
            order.verify(siege).removeDissolvedClan(CLAN_ID);
        }

        @Test
        void destroyClan_shouldRemoveFormerOwnerFromFortSieges_afterFortIsReleased() {
            final Fort fort = mock(Fort.class);
            final FortSiege siege = mock(FortSiege.class);
            WorldLoopExtension.runInPass(() -> clan.setFortId(FORT));
            when(forts.getFortByOwner(clan)).thenReturn(fort);
            when(fortSieges.getSieges()).thenReturn(List.of(siege));

            WorldLoopExtension.runInPass(() -> table.destroyClan(CLAN_ID));

            final InOrder order = inOrder(fort, siege);
            order.verify(fort).removeOwner(true);
            order.verify(siege).removeAttacker(clan);
        }

        @Test
        void destroyClan_shouldRemoveFormerOwnerFromHallSieges_afterHallIsFreed() {
            final SiegableHall hall = mock(SiegableHall.class);
            when(hall.getOwnerId()).thenReturn(CLAN_ID);
            when(hall.isSiegableHall()).thenReturn(true);
            WorldLoopExtension.runInPass(() -> clan.setHideoutId(HALL));
            when(siegableHalls.getConquerableHalls()).thenReturn(Map.of(HALL, hall));

            WorldLoopExtension.runInPass(() -> table.destroyClan(CLAN_ID));

            final InOrder order = inOrder(hall);
            order.verify(hall).free();
            order.verify(hall).removeAttacker(clan);
        }
    }
}
