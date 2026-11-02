/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.siege;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.data.holders.CastleTemplate;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.CastleData;
import app.l2nx.highfive.data.xml.SiegeScheduleData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.CastleManorManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.residence.model.CastleState;
import java.util.ArrayList;
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
class CastleTest {
    private static final int CASTLE_ID = 3;
    private static final int OWNER_CLAN = 268_435_456;

    private final CastleManager castles = mock(CastleManager.class);
    private final TerritoryWarManager territories = mock(TerritoryWarManager.class);
    private final ClanTable clans = mock(ClanTable.class);
    private final List<MockedStatic<?>> bridges = new ArrayList<>();

    @BeforeEach
    void bindSingletons() {
        final SkillTreeData skillTree = mock(SkillTreeData.class);
        when(skillTree.getAvailableResidentialSkills(anyInt())).thenReturn(List.of());
        final CastleData castleData = mock(CastleData.class);
        when(castleData.getCastle(CASTLE_ID)).thenReturn(new CastleTemplate(CASTLE_ID, "Giran", List.of()));
        when(castles.findState(CASTLE_ID))
                .thenReturn(CastleState.builder()
                        .castleId(CASTLE_ID)
                        .ownerClanId(OWNER_CLAN)
                        .build());

        bind(SkillTreeData.class, SkillTreeData::getInstance, skillTree);
        bind(CastleData.class, CastleData::getInstance, castleData);
        bind(CastleManager.class, CastleManager::getInstance, castles);
        bind(ZoneManager.class, ZoneManager::getInstance, mock(ZoneManager.class));
        bind(CastleManorManager.class, CastleManorManager::getInstance, mock(CastleManorManager.class));
        bind(SiegeScheduleData.class, SiegeScheduleData::getInstance, mock(SiegeScheduleData.class));
        bind(TerritoryWarManager.class, TerritoryWarManager::getInstance, territories);
        bind(ClanTable.class, ClanTable::getInstance, clans);
    }

    @AfterEach
    void releaseSingletons() {
        bridges.forEach(MockedStatic::close);
    }

    private <T> void bind(Class<T> type, MockedStatic.Verification getInstance, T instance) {
        final MockedStatic<T> bridge = mockStatic(type);
        bridge.when(getInstance).thenReturn(instance);
        bridges.add(bridge);
    }

    @Nested
    class OwnershipCheck {
        private Castle castle;

        // After the outer stubs: the castle loads its state from them.
        @BeforeEach
        void createCastle() {
            castle = WorldLoopExtension.callInPass(() -> new Castle(CASTLE_ID));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Castle> call) {
            assertThatThrownBy(() -> call.accept(castle))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Castle>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getOwnerId (accessor)", Castle::getOwnerId),
                    Named.of("getDoors", Castle::getDoors),
                    Named.of("getDoor", c -> c.getDoor(1)),
                    Named.of("getSiegeDate", Castle::getSiegeDate),
                    Named.of("setSiegeDate", c -> c.setSiegeDate(0)),
                    Named.of("setTimeRegistrationOverDate", c -> c.setTimeRegistrationOverDate(0)),
                    Named.of("isTimeRegistrationOver", Castle::isTimeRegistrationOver),
                    Named.of("setTimeRegistrationOver", c -> c.setTimeRegistrationOver(false)),
                    Named.of("getTaxPercent", Castle::getTaxPercent),
                    Named.of("getTaxRate", Castle::getTaxRate),
                    Named.of("getTreasury", Castle::getTreasury),
                    Named.of("isArtefact", c -> c.isArtefact(null)),
                    Named.of("registerArtefact", c -> c.registerArtefact(null)),
                    Named.of("engrave (former synchronized)", c -> c.engrave(null, null)),
                    Named.of("getTicketBuyCount", Castle::getTicketBuyCount),
                    Named.of("setTicketBuyCount", c -> c.setTicketBuyCount(1)),
                    Named.of("isFirstMidVictory", Castle::isFirstMidVictory),
                    Named.of("setFirstMidVictory", c -> c.setFirstMidVictory(true)),
                    Named.of("addToTreasury", c -> c.addToTreasury(1)),
                    Named.of("addToTreasuryNoTax", c -> c.addToTreasuryNoTax(1)),
                    Named.of("getFunction", c -> c.getFunction(Castle.FUNC_TELEPORT)),
                    Named.of("removeFunction", c -> c.removeFunction(Castle.FUNC_TELEPORT)),
                    Named.of("updateFunctions", c -> c.updateFunctions(null, 1, 1, 0, 1, true)),
                    Named.of("removeUpgrade", Castle::removeUpgrade),
                    Named.of("setOwner", c -> c.setOwner(null)),
                    Named.of("removeOwner", c -> c.removeOwner(null)),
                    Named.of("getOwner", Castle::getOwner),
                    Named.of("setTaxPercent", c -> c.setTaxPercent(1)),
                    Named.of("spawnDoor", Castle::spawnDoor),
                    Named.of("activateInstance", Castle::activateInstance),
                    Named.of("setDoorUpgrade", c -> c.setDoorUpgrade(1, 1, false)),
                    Named.of("openCloseDoor", c -> c.openCloseDoor(null, 1, true)),
                    Named.of("getSiege", Castle::getSiege),
                    Named.of("getZone", Castle::getZone),
                    Named.of("getResidenceZone (override)", Castle::getResidenceZone),
                    Named.of("getTeleZone", Castle::getTeleZone),
                    Named.of("getTimeRegistrationOverDate", Castle::getTimeRegistrationOverDate),
                    Named.of("getShowNpcCrest", Castle::getShowNpcCrest),
                    Named.of("setShowNpcCrest", c -> c.setShowNpcCrest(true)),
                    Named.of("updateClansReputation", Castle::updateClansReputation),
                    Named.of("giveResidentialSkills (override)", c -> c.giveResidentialSkills(null)),
                    Named.of("removeResidentialSkills (override)", c -> c.removeResidentialSkills(null)),
                    Named.of("getTrapUpgradeLevel", c -> c.getTrapUpgradeLevel(0)),
                    Named.of("setTrapUpgrade", c -> c.setTrapUpgrade(0, 1, false)));
        }
    }

    @Nested
    class Functions {
        private final Player player = mock(Player.class);

        @BeforeEach
        void payWithAdena() {
            when(player.destroyItemByItemId(any(), anyInt(), anyLong(), any(), anyBoolean()))
                    .thenReturn(true);
        }

        @Test
        void removeOwner_shouldRemoveEveryFunctionAndItsFee(Loop world) {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final Castle castle = new Castle(CASTLE_ID);
                castle.updateFunctions(player, Castle.FUNC_TELEPORT, 1, 0, 86_400_000L, true);
                castle.updateFunctions(player, Castle.FUNC_RESTORE_HP, 1, 0, 86_400_000L, true);
                final int before = world.timers(TimeBase.GAME).size();
                castle.removeOwner(null);
                return Arrays.asList(
                        before - world.timers(TimeBase.GAME).size(),
                        castle.getFunction(Castle.FUNC_TELEPORT),
                        castle.getFunction(Castle.FUNC_RESTORE_HP));
            });

            assertThat(actual).containsExactly(2, null, null);
            verify(castles).deleteFunction(CASTLE_ID, Castle.FUNC_TELEPORT);
            verify(castles).deleteFunction(CASTLE_ID, Castle.FUNC_RESTORE_HP);
        }
    }

    @Nested
    class SiegeDate {
        @Test
        void getSiegeDate_shouldHandOutACopy() {
            final long siegeAt =
                    ClockBridgeExtension.WALL_START.plusSeconds(86_400).toEpochMilli();

            final long actual = WorldLoopExtension.callInPass(() -> {
                final Castle castle = new Castle(CASTLE_ID);
                castle.setSiegeDate(siegeAt);
                castle.getSiegeDate().setTimeInMillis(0);
                return castle.getSiegeDate().getTimeInMillis();
            });

            assertThat(actual).isEqualTo(siegeAt);
        }

        @Test
        void setSiegeDate_shouldReplaceTheDateTheSiegeReads() {
            final long siegeAt =
                    ClockBridgeExtension.WALL_START.plusSeconds(86_400).toEpochMilli();

            final long actual = WorldLoopExtension.callInPass(() -> {
                final Castle castle = new Castle(CASTLE_ID);
                castle.setSiegeDate(siegeAt);
                return castle.getSiege().getSiegeDate().getTimeInMillis();
            });

            assertThat(actual).isEqualTo(siegeAt);
        }

        @Test
        void getTimeRegistrationOverDate_shouldHandOutACopy() {
            final long registrationEnd =
                    ClockBridgeExtension.WALL_START.plusSeconds(86_400).toEpochMilli();

            final long actual = WorldLoopExtension.callInPass(() -> {
                final Castle castle = new Castle(CASTLE_ID);
                castle.setTimeRegistrationOverDate(registrationEnd);
                castle.getTimeRegistrationOverDate().setTimeInMillis(0);
                return castle.getTimeRegistrationOverDate().getTimeInMillis();
            });

            assertThat(actual).isEqualTo(registrationEnd);
        }

        @Test
        void setTimeRegistrationOverDate_shouldReplaceTheDate() {
            final long registrationEnd =
                    ClockBridgeExtension.WALL_START.plusSeconds(86_400).toEpochMilli();

            final long actual = WorldLoopExtension.callInPass(() -> {
                final Castle castle = new Castle(CASTLE_ID);
                castle.setTimeRegistrationOverDate(registrationEnd);
                return castle.getTimeRegistrationOverDate().getTimeInMillis();
            });

            assertThat(actual).isEqualTo(registrationEnd);
        }
    }

    @Nested
    class RemoveOwner {
        private final Clan owner = mock(Clan.class);

        @Test
        void removeOwner_shouldClearTerritoryOwner_whenCastleHasTerritory() {
            final TerritoryWarManager.Territory territory = mock(TerritoryWarManager.Territory.class);
            when(territories.getTerritory(CASTLE_ID)).thenReturn(territory);
            final int actual = WorldLoopExtension.callInPass(() -> {
                final Castle castle = new Castle(CASTLE_ID);
                castle.removeOwner(owner);
                return castle.getOwnerId();
            });

            assertThat(actual).isZero();
            verify(territory).setOwnerClan(null);
            verify(castles).saveOwner(CASTLE_ID, 0);
        }

        @Test
        void removeOwner_shouldRemoveOwner_whenCastleHasNoTerritory() {
            final int actual = WorldLoopExtension.callInPass(() -> {
                final Castle castle = new Castle(CASTLE_ID);
                castle.removeOwner(owner);
                return castle.getOwnerId();
            });

            assertThat(actual).isZero();
            verify(castles).saveOwner(CASTLE_ID, 0);
        }
    }

    @Nested
    class UpdateClansReputation {
        private static final int WINNER_CLAN = 268_435_457;

        private final Clan dissolved = mock(Clan.class);
        private final Clan winner = mock(Clan.class);

        @Test
        void updateClansReputation_shouldRewardWinnerInFull_whenFormerOwnerIsDissolved() {
            when(dissolved.getId()).thenReturn(OWNER_CLAN);
            when(winner.getId()).thenReturn(WINNER_CLAN);
            when(clans.getClan(WINNER_CLAN)).thenReturn(winner);
            when(territories.getTerritory(CASTLE_ID)).thenReturn(mock(TerritoryWarManager.Territory.class));
            WorldLoopExtension.runInPass(() -> {
                final Castle castle = new Castle(CASTLE_ID);
                castle.removeOwner(dissolved);
                castle.setOwner(winner);
                castle.updateClansReputation();
            });

            verify(dissolved, never()).takeReputationScore(anyInt());
            verify(winner).addReputationScore(FeatureConfig.TAKE_CASTLE_POINTS);
        }
    }
}
