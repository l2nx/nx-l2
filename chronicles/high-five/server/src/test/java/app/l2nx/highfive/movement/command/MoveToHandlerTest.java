/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.action.view.ActionCancelled;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.view.MessageShown;
import app.l2nx.highfive.movement.MoveInput;
import app.l2nx.highfive.session.GameContexts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

class MoveToHandlerTest {
    private static final MoveTo MOUSE_MOVE = MoveTo.builder()
            .targetX(10)
            .targetY(20)
            .targetZ(30)
            .originX(1)
            .originY(2)
            .originZ(3)
            .input(MoveInput.MOUSE)
            .build();

    private final Player player = mock(Player.class);
    private final ClientPort port = mock(ClientPort.class);
    private final MoveToHandler handler = new MoveToHandler();

    @BeforeEach
    void setUp() {
        when(player.getPort()).thenReturn(port);
    }

    @Nested
    class Context {
        @Test
        void context_shouldBeThePlayerInTheWorld() {
            assertThat(handler.context()).isSameAs(GameContexts.WORLD);
        }
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldRefuseAnOverloadedPlayerWithTheMessageAndTheCancel() {
            when(player.isOverloaded()).thenReturn(true);

            handler.handle(player, MOUSE_MOVE);

            verify(port).emit(MessageShown.of(MessageKey.CANNOT_MOVE_TOO_ENCUMBERED));
            verify(port).emit(ActionCancelled.INSTANCE);
            verify(player, never()).getAI();
        }

        @Test
        void handle_shouldStopAndCancel_whenTheTargetIsTheOrigin() {
            final Location here = new Location(1, 2, 3);
            when(player.getLocation()).thenReturn(here);

            handler.handle(
                    player,
                    MOUSE_MOVE.toBuilder().targetX(1).targetY(2).targetZ(3).build());

            verify(player).stopMove(here);
            verify(port).emit(ActionCancelled.INSTANCE);
            verify(player, never()).getAI();
        }

        @ParameterizedTest(name = "origin offset ({0}, {1}) synced: {2}")
        @CsvSource({"500,0,true", "0,-999,true", "1500,0,false", "65536,0,false", "65536,65536,false"})
        void handle_shouldSyncToTheKeyboardOrigin_whenItIsNear(int offsetX, int offsetY, boolean synced) {
            when(player.inObserverMode()).thenReturn(true);
            final MoveTo move = MOUSE_MOVE.toBuilder()
                    .targetX(50_000)
                    .originX(offsetX)
                    .originY(offsetY)
                    .input(MoveInput.KEYBOARD)
                    .build();

            try (MockedStatic<GeoEngine> geo = mockStatic(GeoEngine.class);
                    MockedStatic<DoorData> doors = mockStatic(DoorData.class)) {
                geo.when(GeoEngine::getInstance).thenReturn(mock(GeoEngine.class));
                doors.when(DoorData::getInstance).thenReturn(mock(DoorData.class));

                handler.handle(player, move);
            }

            if (synced) {
                verify(player).setSyncedXYZ(offsetX, offsetY, 3);
            } else {
                verify(player, never()).setSyncedXYZ(anyInt(), anyInt(), anyInt());
            }
            verify(player).setCursorKeyMovement(true);
            verify(port).emit(ActionCancelled.INSTANCE);
            verify(player, never()).getAI();
        }
    }
}
