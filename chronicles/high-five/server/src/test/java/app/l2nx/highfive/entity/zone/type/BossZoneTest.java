/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.zone.type;

import static app.l2nx.highfive.entity.zone.ZoneTestObjects.player;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneTestObjects;
import app.l2nx.highfive.managers.GrandBossManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
class BossZoneTest {
    private final Player first = player(268_500_003);
    private final Player second = player(268_500_001);
    private final Player third = player(268_500_002);

    static BossZone bossZone() {
        return WorldLoopExtension.callInPass(() -> {
            try (MockedStatic<GrandBossManager> manager = mockStatic(GrandBossManager.class)) {
                manager.when(GrandBossManager::getInstance).thenReturn(mock(GrandBossManager.class));
                final BossZone zone = new BossZone(70_201);
                zone.setZone(ZoneTestObjects.boxAroundOrigin());
                zone.setParameter("oustX", "1000");
                zone.setParameter("oustY", "2000");
                zone.setParameter("oustZ", "-300");
                return zone;
            }
        });
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<BossZone> call) {
            final BossZone zone = bossZone();

            assertThatThrownBy(() -> call.accept(zone))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<BossZone>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getTimeInvade (accessor)", BossZone::getTimeInvade),
                    Named.of("getSettings (accessor)", BossZone::getSettings),
                    Named.of("setParameter", z -> z.setParameter("InvadeTime", "1000")),
                    Named.of("setEnabled", z -> z.setEnabled(false)),
                    Named.of("setAllowedPlayers", z -> z.setAllowedPlayers(List.of(1))),
                    Named.of("getAllowedPlayers", BossZone::getAllowedPlayers),
                    Named.of("isPlayerAllowed", z -> z.isPlayerAllowed(player(1))),
                    Named.of("movePlayersTo", z -> z.movePlayersTo(null)),
                    Named.of("oustAllPlayers", BossZone::oustAllPlayers),
                    Named.of("allowPlayerEntry", z -> z.allowPlayerEntry(player(1), 60)),
                    Named.of("removePlayer", z -> z.removePlayer(player(1))));
        }

        @Test
        void settingsClear_shouldThrow_whenCalledOffTheLoop() {
            final BossZone.Settings settings = WorldLoopExtension.callInPass(bossZone()::getSettings);

            assertThatThrownBy(settings::clear)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }
    }

    @Nested
    class GetAllowedPlayers {
        @Test
        void getAllowedPlayers_shouldBeAReadOnlySnapshotInAdmissionOrder() {
            final BossZone zone = bossZone();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                zone.allowPlayerEntry(first, 60);
                zone.allowPlayerEntry(second, 60);
                zone.allowPlayerEntry(third, 60);
                zone.allowPlayerEntry(first, 60);
                final List<Integer> snapshot = zone.getAllowedPlayers();
                zone.removePlayer(second);
                return snapshot;
            });

            assertThat(actual).containsExactly(268_500_003, 268_500_001, 268_500_002);
            assertThatThrownBy(() -> actual.add(1)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void setAllowedPlayers_shouldReplaceTheEntrantsInTheGivenOrder() {
            final BossZone zone = bossZone();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                zone.allowPlayerEntry(first, 60);
                zone.setAllowedPlayers(List.of(7, 5, 6));
                return zone.getAllowedPlayers();
            });

            assertThat(actual).containsExactly(7, 5, 6);
        }
    }

    @Nested
    class Enter {
        @Test
        void revalidateInZone_shouldLetAnAdmittedPlayerIn_whenTheEntryHasNotExpired() {
            final BossZone zone = bossZone();

            WorldLoopExtension.runInPass(() -> {
                zone.allowPlayerEntry(first, 60);
                zone.revalidateInZone(first);
            });

            verify(first, never()).teleToLocation(anyInt(), anyInt(), anyInt());
        }

        @Test
        void revalidateInZone_shouldOustAPlayerNotAdmitted() {
            final BossZone zone = bossZone();

            WorldLoopExtension.runInPass(() -> zone.revalidateInZone(first));

            verify(first).teleToLocation(1000, 2000, -300);
        }

        @Test
        void isPlayerAllowed_shouldOustAndAnswerFalse_whenThePlayerWasRemoved() {
            final BossZone zone = bossZone();

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                zone.allowPlayerEntry(first, 60);
                zone.removePlayer(first);
                return zone.isPlayerAllowed(first);
            });

            assertThat(actual).isFalse();
            verify(first).teleToLocation(1000, 2000, -300);
        }
    }

    @Nested
    class OustAllPlayers {
        @Test
        void oustAllPlayers_shouldOustEveryPlayerAndForgetTheEntrants() {
            final BossZone zone = bossZone();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                zone.allowPlayerEntry(first, 60);
                zone.allowPlayerEntry(second, 60);
                zone.revalidateInZone(first);
                zone.revalidateInZone(second);
                zone.oustAllPlayers();
                return zone.getAllowedPlayers();
            });

            verify(first).teleToLocation(1000, 2000, -300);
            verify(second).teleToLocation(1000, 2000, -300);
            assertThat(actual).isEmpty();
        }
    }
}
