/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.siege;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.holders.FortTemplate;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.FortData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.data.xml.StaticObjectData;
import app.l2nx.highfive.entity.actor.instance.StaticObject;
import app.l2nx.highfive.entity.zone.type.FortZone;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.repository.residence.FortDoorUpgradeRepository;
import app.l2nx.highfive.repository.residence.FortFunctionRepository;
import app.l2nx.highfive.repository.residence.FortStateRepository;
import app.l2nx.highfive.repository.residence.model.FortState;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.util.List;
import java.util.Optional;
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
class FortTest {
    private static final int FORT_ID = 101;
    private static final String FORT_NAME = "Shanty";
    private static final int UNLOADED_CLAN = 268_435_456;
    private static final int CONTRACTED_CASTLE = 1;
    private static final int SUPPLY_LEVEL = 5;

    private final FortStateRepository states = mock(FortStateRepository.class);
    private final FortFunctionRepository functions = mock(FortFunctionRepository.class);

    private MockedStatic<SkillTreeData> skillTreeData;
    private MockedStatic<FortData> fortData;
    private MockedStatic<StaticObjectData> staticObjectData;
    private MockedStatic<ZoneManager> zoneManager;
    private MockedStatic<ClanTable> clanTable;

    @BeforeEach
    void bindSingletons() {
        new FortManager(states, functions, mock(FortDoorUpgradeRepository.class));

        final SkillTreeData skillTree = mock(SkillTreeData.class);
        when(skillTree.getAvailableResidentialSkills(anyInt())).thenReturn(List.of());
        skillTreeData = mockStatic(SkillTreeData.class);
        skillTreeData.when(SkillTreeData::getInstance).thenReturn(skillTree);

        final FortData forts = mock(FortData.class);
        when(forts.getFort(FORT_ID)).thenReturn(new FortTemplate(FORT_ID, FORT_NAME, 0, List.of(), List.of()));
        fortData = mockStatic(FortData.class);
        fortData.when(FortData::getInstance).thenReturn(forts);

        final StaticObject flagPole = mock(StaticObject.class);
        when(flagPole.getType()).thenReturn(3);
        when(flagPole.getName()).thenReturn(FORT_NAME + " Flagpole");
        final StaticObjectData staticObjects = mock(StaticObjectData.class);
        when(staticObjects.getStaticObjects()).thenReturn(List.of(flagPole));
        staticObjectData = mockStatic(StaticObjectData.class);
        staticObjectData.when(StaticObjectData::getInstance).thenReturn(staticObjects);

        final ZoneManager zones = mock(ZoneManager.class);
        when(zones.getAllZones(FortZone.class)).thenReturn(List.of());
        zoneManager = mockStatic(ZoneManager.class);
        zoneManager.when(ZoneManager::getInstance).thenReturn(zones);

        clanTable = mockStatic(ClanTable.class);
        clanTable.when(ClanTable::getInstance).thenReturn(mock(ClanTable.class));
    }

    @AfterEach
    void releaseSingletons() {
        clanTable.close();
        zoneManager.close();
        staticObjectData.close();
        fortData.close();
        skillTreeData.close();
    }

    private void storeState(int ownerClanId) {
        when(states.findById(FORT_ID))
                .thenReturn(Optional.of(FortState.builder()
                        .fortId(FORT_ID)
                        .ownerClanId(ownerClanId)
                        .lastOwnedTime(ClockBridgeExtension.WALL_START.minusSeconds(3_600))
                        .contractState(2)
                        .contractedCastleId(CONTRACTED_CASTLE)
                        .supplyLevel(SUPPLY_LEVEL)
                        .build()));
    }

    @Nested
    class Load {
        @Test
        void construct_shouldLeaveFortWithoutOwner_whenOwnerClanIsNotLoaded() {
            storeState(UNLOADED_CLAN);

            final Fort actual = new Fort(FORT_ID);

            assertThat(actual.getOwnerClan()).isNull();
            assertThat(actual.getOwnedTime()).isZero();
            assertThat(actual.getFortState()).isZero();
            assertThat(actual.getContractedCastleId()).isZero();
            assertThat(actual.getSupplyLeveL()).isZero();
        }

        @Test
        void construct_shouldPersistClearedOwner_whenOwnerClanIsNotLoaded() {
            storeState(UNLOADED_CLAN);
            when(functions.findByFort(FORT_ID))
                    .thenReturn(List.of(ResidenceFunction.builder()
                            .type(Fort.FUNC_TELEPORT)
                            .level(1)
                            .build()));

            new Fort(FORT_ID);

            verify(states).updateOwner(FORT_ID, 0, null);
            verify(states).updateSupplyLevel(FORT_ID, 0);
            verify(functions).delete(FORT_ID, Fort.FUNC_TELEPORT);
        }

        @Test
        void construct_shouldNotPersist_whenFortHasNoOwner() {
            storeState(0);

            new Fort(FORT_ID);

            verify(states, never()).updateOwner(anyInt(), anyInt(), any());
            verify(states, never()).updateSupplyLevel(anyInt(), anyInt());
            verifyNoInteractions(functions);
        }
    }
}
