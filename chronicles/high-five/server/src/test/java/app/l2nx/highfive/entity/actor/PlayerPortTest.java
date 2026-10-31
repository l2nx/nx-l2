/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.network.GameClient;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class PlayerPortTest {
    private final Player player = mock(Player.class, CALLS_REAL_METHODS);
    private final GameClient client = mock(GameClient.class);
    private final ClientPort port = mock(ClientPort.class);

    @Nested
    class GetPort {
        @Test
        void getPort_shouldBeTheClientPort() {
            when(client.port()).thenReturn(port);
            player.setClient(client);

            assertThat(player.getPort()).isSameAs(port);
        }

        @Test
        void getPort_shouldBeDetached_whenThePlayerHasNoClient() {
            player.setClient(null);

            assertThat(player.getPort()).isSameAs(ClientPort.DETACHED);
        }

        @Test
        void getPort_shouldBeDetached_whenTheClientIsDetached() {
            when(client.isDetached()).thenReturn(true);
            player.setClient(client);

            assertThat(player.getPort()).isSameAs(ClientPort.DETACHED);
        }
    }

    @Nested
    class ObserverPorts {
        @ParameterizedTest(name = "includeSelf {0}")
        @ValueSource(booleans = {true, false})
        void observerPorts_shouldBeThePortsOfThePlayersWhoSeeIt(boolean includeSelf) {
            when(client.port()).thenReturn(port);
            player.setClient(client);
            final Player watching = observer();
            final Player blind = observer();
            doReturn(true).when(player).isVisibleFor(watching);
            doReturn(false).when(player).isVisibleFor(blind);

            final List<ClientPort> actual;
            try (MockedStatic<World> world = mockStatic(World.class)) {
                world.when(() -> World.forEachVisibleObject(eq(player), eq(Player.class), any()))
                        .thenAnswer(invocation -> {
                            final Consumer<Player> action = invocation.getArgument(2);
                            action.accept(watching);
                            action.accept(blind);
                            return null;
                        });

                actual = player.observerPorts(includeSelf);
            }

            assertThat(actual).isEqualTo(includeSelf ? List.of(port, watching.getPort()) : List.of(watching.getPort()));
        }

        private static Player observer() {
            final Player observer = mock(Player.class);
            when(observer.getPort()).thenReturn(mock(ClientPort.class));
            return observer;
        }
    }
}
