/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.siege;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.residence.FortDoorUpgradeRepository;
import app.l2nx.highfive.repository.residence.FortFunctionRepository;
import app.l2nx.highfive.repository.residence.FortStateRepository;
import app.l2nx.highfive.repository.residence.model.FortState;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
    class OwnershipCheck {
        private Fort fort;

        // After the outer stubs: the fort loads its state from them.
        @BeforeEach
        void createFort() {
            storeState(0);
            fort = WorldLoopExtension.callInPass(() -> new Fort(FORT_ID));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Fort> call) {
            assertThatThrownBy(() -> call.accept(fort))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Fort>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getOwnerClan (accessor)", Fort::getOwnerClan),
                    Named.of("setOwnerClan", f -> f.setOwnerClan(null)),
                    Named.of("getDoors", Fort::getDoors),
                    Named.of("getDoor", f -> f.getDoor(1)),
                    Named.of("getFlagPole", Fort::getFlagPole),
                    Named.of("getSiegeDate", Fort::getSiegeDate),
                    Named.of("setSiegeDate", f -> f.setSiegeDate(0)),
                    Named.of("getFortType", Fort::getFortType),
                    Named.of("getFortState", Fort::getFortState),
                    Named.of("setFortState", f -> f.setFortState(0, 0)),
                    Named.of("getContractedCastleId", Fort::getContractedCastleId),
                    Named.of("getSupplyLeveL", Fort::getSupplyLeveL),
                    Named.of("raiseSupplyLeveL", Fort::raiseSupplyLeveL),
                    Named.of("setSupplyLeveL", f -> f.setSupplyLeveL(1)),
                    Named.of("getFunction", f -> f.getFunction(Fort.FUNC_TELEPORT)),
                    Named.of("removeFunction", f -> f.removeFunction(Fort.FUNC_TELEPORT)),
                    Named.of("updateFunctions", f -> f.updateFunctions(null, 1, 1, 0, 1, true)),
                    Named.of("setOwner", f -> f.setOwner(null, false)),
                    Named.of("removeOwner", f -> f.removeOwner(false)),
                    Named.of("endOfSiege", f -> f.endOfSiege(null)),
                    Named.of("resetDoors", Fort::resetDoors),
                    Named.of("upgradeDoor", f -> f.upgradeDoor(1, 1, 1, 1)),
                    Named.of("getSiege (former synchronized)", Fort::getSiege),
                    Named.of("getOwnedTime", Fort::getOwnedTime),
                    Named.of("getTimeTillNextFortUpdate", Fort::getTimeTillNextFortUpdate),
                    Named.of("getCastleIdByAmbassador", f -> f.getCastleIdByAmbassador(1)),
                    Named.of("isBorderFortress", Fort::isBorderFortress),
                    Named.of("spawnSuspiciousMerchant", Fort::spawnSuspiciousMerchant),
                    Named.of("spawnNpcCommanders", Fort::spawnNpcCommanders),
                    Named.of("despawnNpcCommanders", Fort::despawnNpcCommanders),
                    Named.of("spawnSpecialEnvoys", Fort::spawnSpecialEnvoys),
                    Named.of("getResidenceZone (override)", Fort::getResidenceZone));
        }
    }

    @Nested
    class Ambassadors {
        @Test
        void getCastleIdByAmbassador_shouldBeMinusOne_whenTheNpcIsNoEnvoy() {
            storeState(0);
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final Fort fort = new Fort(FORT_ID);
                return Arrays.asList(fort.getCastleIdByAmbassador(1), fort.isBorderFortress());
            });

            assertThat(actual).containsExactly(-1, false);
        }
    }

    @Nested
    class SiegeDate {
        @Test
        void getSiegeDate_shouldHandOutACopy() {
            storeState(0);
            final long siegeAt =
                    ClockBridgeExtension.WALL_START.plusSeconds(86_400).toEpochMilli();

            final long actual = WorldLoopExtension.callInPass(() -> {
                final Fort fort = new Fort(FORT_ID);
                fort.setSiegeDate(siegeAt);
                fort.getSiegeDate().setTimeInMillis(0);
                return fort.getSiegeDate().getTimeInMillis();
            });

            assertThat(actual).isEqualTo(siegeAt);
        }

        @Test
        void setSiegeDate_shouldReplaceTheDate() {
            storeState(0);
            final long siegeAt =
                    ClockBridgeExtension.WALL_START.plusSeconds(86_400).toEpochMilli();

            final long actual = WorldLoopExtension.callInPass(() -> {
                final Fort fort = new Fort(FORT_ID);
                fort.setSiegeDate(siegeAt);
                return fort.getSiegeDate().getTimeInMillis();
            });

            assertThat(actual).isEqualTo(siegeAt);
        }
    }

    @Nested
    class Load {
        @Test
        void construct_shouldLeaveFortWithoutOwner_whenOwnerClanIsNotLoaded() {
            storeState(UNLOADED_CLAN);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final Fort fort = new Fort(FORT_ID);
                return Arrays.asList(
                        fort.getOwnerClan(),
                        fort.getOwnedTime(),
                        fort.getFortState(),
                        fort.getContractedCastleId(),
                        fort.getSupplyLeveL());
            });

            assertThat(actual).containsExactly(null, 0, 0, 0, 0);
        }

        @Test
        void construct_shouldPersistClearedOwner_whenOwnerClanIsNotLoaded() {
            storeState(UNLOADED_CLAN);
            when(functions.findByFort(FORT_ID))
                    .thenReturn(List.of(ResidenceFunction.builder()
                            .type(Fort.FUNC_TELEPORT)
                            .level(1)
                            .build()));

            WorldLoopExtension.runInPass(() -> new Fort(FORT_ID));

            verify(states).updateOwner(FORT_ID, 0, null);
            verify(states).updateSupplyLevel(FORT_ID, 0);
            verify(functions).delete(FORT_ID, Fort.FUNC_TELEPORT);
        }

        @Test
        void construct_shouldNotPersist_whenFortHasNoOwner() {
            storeState(0);

            WorldLoopExtension.runInPass(() -> new Fort(FORT_ID));

            verify(states, never()).updateOwner(anyInt(), anyInt(), any());
            verify(states, never()).updateSupplyLevel(anyInt(), anyInt());
            verifyNoInteractions(functions);
        }
    }
}
