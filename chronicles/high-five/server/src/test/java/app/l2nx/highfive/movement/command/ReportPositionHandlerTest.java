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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.stat.PlayerStat;
import app.l2nx.highfive.movement.view.PositionCorrected;
import app.l2nx.highfive.session.GameContexts;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

class ReportPositionHandlerTest {
    private static final int REAL_X = 1_000;

    private final Player player = mock(Player.class);
    private final ClientPort port = mock(ClientPort.class);
    private final ReportPositionHandler handler = new ReportPositionHandler();

    @BeforeEach
    void setUp() {
        when(player.getPort()).thenReturn(port);
        when(player.getX()).thenReturn(REAL_X);
        when(player.getStat()).thenReturn(mock(PlayerStat.class));
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
        void handle_shouldIgnoreAReport_whenTheServerTeleportsThePlayer() {
            when(player.isTeleporting()).thenReturn(true);

            handler.handle(
                    player, ReportPosition.builder().x(5).y(6).z(7).heading(8).build());

            verify(player, never()).setClientX(anyInt());
            verifyNoInteractions(port);
        }

        @Test
        void handle_shouldIgnoreAZeroPosition_whenTheServerHasOne() {
            handler.handle(
                    player, ReportPosition.builder().x(0).y(0).z(7).heading(8).build());

            verify(player, never()).setClientX(anyInt());
        }

        @ParameterizedTest(name = "drift {0} corrected: {1}")
        @CsvSource({"0,false", "299,false", "301,true", "65536,true"})
        void handle_shouldCorrectAFlyingPlayer_whenPastTheFlyingDrift(int drift, boolean corrected) {
            when(player.isFlying()).thenReturn(true);

            handle(ReportPosition.builder().x(REAL_X + drift).heading(8).build());

            if (corrected) {
                verify(port).emit(PositionCorrected.of(player));
            } else {
                verifyNoInteractions(port);
            }
            verify(player).setClientX(REAL_X + drift);
            verify(player).setClientHeading(8);
        }

        @Test
        void handle_shouldFollowAKeyboardMoveAndShowItToThePlayerAndItsObservers() {
            final ClientPort observer = mock(ClientPort.class);
            when(player.isCursorKeyMovement()).thenReturn(true);
            when(player.observerPorts(true)).thenReturn(List.of(port, observer));

            handle(ReportPosition.builder().x(REAL_X + 10).y(20).z(30).build());

            verify(player).setSyncedXYZ(REAL_X + 10, 20, 30);
            verify(port).emit(PositionCorrected.of(player));
            verify(observer).emit(PositionCorrected.of(player));
        }
    }

    private void handle(ReportPosition command) {
        try (MockedStatic<DoorData> doors = mockStatic(DoorData.class)) {
            doors.when(DoorData::getInstance).thenReturn(mock(DoorData.class));
            handler.handle(player, command);
        }
    }
}
