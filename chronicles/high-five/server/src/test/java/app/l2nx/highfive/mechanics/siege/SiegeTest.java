/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.siege;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.data.xml.SiegeScheduleData;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.repository.siege.model.CastleSiegeClan;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

@Execution(ExecutionMode.SAME_THREAD)
class SiegeTest {
    private static final int CASTLE_ID = 3;
    private static final int OWNER = 268_435_456;
    private static final int ATTACKER = 268_435_457;
    private static final int DEFENDER = 268_435_458;
    private static final int PENDING = 268_435_459;
    private static final int UNREGISTERED = 268_435_460;

    private final Castle castle = mock(Castle.class);
    private final SiegeManager sieges = mock(SiegeManager.class);
    private final List<MockedStatic<?>> bridges = new ArrayList<>();

    private Siege siege;

    @BeforeEach
    void loadSiege() {
        bind(SiegeScheduleData.class, SiegeScheduleData::getInstance, mock(SiegeScheduleData.class));
        bind(SiegeManager.class, SiegeManager::getInstance, sieges);
        when(castle.getResidenceId()).thenReturn(CASTLE_ID);
        when(castle.getOwnerId()).thenReturn(OWNER);
        when(sieges.findSiegeClans(CASTLE_ID))
                .thenReturn(List.of(
                        new CastleSiegeClan(ATTACKER, SiegeClanType.ATTACKER),
                        new CastleSiegeClan(DEFENDER, SiegeClanType.DEFENDER),
                        new CastleSiegeClan(PENDING, SiegeClanType.DEFENDER_PENDING)));
        siege = new Siege(castle);
        siege.removeSiegeClan(UNREGISTERED);
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

    private List<Integer> sideClanIds() {
        return Stream.of(siege.getAttackerClans(), siege.getDefenderClans(), siege.getDefenderWaitingClans())
                .flatMap(Collection::stream)
                .map(SiegeClan::getClanId)
                .toList();
    }

    @Nested
    class RemoveDissolvedClan {
        @ParameterizedTest(name = "clan {0}")
        @ValueSource(ints = {OWNER, ATTACKER, DEFENDER, PENDING})
        void removeDissolvedClan_shouldDropClanFromItsSideOnly(int clanId) {
            final List<Integer> expected = new ArrayList<>(List.of(OWNER, ATTACKER, DEFENDER, PENDING));
            expected.remove(Integer.valueOf(clanId));
            assertThat(sideClanIds()).containsExactlyInAnyOrder(OWNER, ATTACKER, DEFENDER, PENDING);

            siege.removeDissolvedClan(clanId);

            assertThat(sideClanIds()).containsExactlyInAnyOrderElementsOf(expected);
        }
    }
}
