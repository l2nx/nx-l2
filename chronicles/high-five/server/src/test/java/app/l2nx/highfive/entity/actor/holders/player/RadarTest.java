/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RadarControl;
import app.l2nx.highfive.partition.WorldLoopExtension;
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

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class RadarTest {
    private final Player player = mock(Player.class);
    private final Radar radar = new Radar(player);

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Radar> call) {
            assertThatThrownBy(() -> call.accept(radar))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Radar>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("addMarker", radar -> radar.addMarker(1, 2, 3)),
                    Named.of("removeMarker", radar -> radar.removeMarker(1, 2, 3)),
                    Named.of("removeAllMarkers", Radar::removeAllMarkers),
                    Named.of("loadMarkers", Radar::loadMarkers));
        }
    }

    @Nested
    class Markers {
        @Test
        void loadMarkers_shouldShowEachMarkerOnce_whenAddedTwice() {
            WorldLoopExtension.runInPass(() -> {
                radar.addMarker(1, 2, 3);
                radar.addMarker(1, 2, 3);
                radar.addMarker(4, 5, 6);
            });
            clearInvocations(player);

            WorldLoopExtension.runInPass(radar::loadMarkers);

            verify(player, times(3)).sendPacket(any(RadarControl.class));
        }

        @Test
        void removeMarker_shouldDropTheMarkerAtThosePoints() {
            WorldLoopExtension.runInPass(() -> {
                radar.addMarker(1, 2, 3);
                radar.addMarker(4, 5, 6);
                radar.removeMarker(1, 2, 3);
            });
            clearInvocations(player);

            WorldLoopExtension.runInPass(radar::loadMarkers);

            verify(player, times(2)).sendPacket(any(RadarControl.class));
        }

        @Test
        void removeAllMarkers_shouldClearEveryMarker() {
            WorldLoopExtension.runInPass(() -> {
                radar.addMarker(1, 2, 3);
                radar.addMarker(4, 5, 6);
            });
            clearInvocations(player);

            WorldLoopExtension.runInPass(() -> {
                radar.removeAllMarkers();
                radar.loadMarkers();
            });

            verify(player, times(3)).sendPacket(any(RadarControl.class));
        }
    }
}
