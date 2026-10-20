/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.siege;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.data.holders.CastleTemplate;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.CastleData;
import app.l2nx.highfive.data.xml.SiegeScheduleData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.CastleManorManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.repository.residence.model.CastleState;
import java.util.ArrayList;
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
    class RemoveOwner {
        private final Clan owner = mock(Clan.class);

        @Test
        void removeOwner_shouldClearTerritoryOwner_whenCastleHasTerritory() {
            final TerritoryWarManager.Territory territory = mock(TerritoryWarManager.Territory.class);
            when(territories.getTerritory(CASTLE_ID)).thenReturn(territory);
            final Castle castle = new Castle(CASTLE_ID);

            castle.removeOwner(owner);

            assertThat(castle.getOwnerId()).isZero();
            verify(territory).setOwnerClan(null);
            verify(castles).saveOwner(CASTLE_ID, 0);
        }

        @Test
        void removeOwner_shouldRemoveOwner_whenCastleHasNoTerritory() {
            final Castle castle = new Castle(CASTLE_ID);

            castle.removeOwner(owner);

            assertThat(castle.getOwnerId()).isZero();
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
            final Castle castle = new Castle(CASTLE_ID);
            castle.removeOwner(dissolved);
            castle.setOwner(winner);

            castle.updateClansReputation();

            verify(dissolved, never()).takeReputationScore(anyInt());
            verify(winner).addReputationScore(FeatureConfig.TAKE_CASTLE_POINTS);
        }
    }
}
