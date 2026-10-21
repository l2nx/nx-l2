/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.network.LoginClient;
import app.l2nx.network.metrics.model.DropReason;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LanePacketTest {
    private final LoginClient client = mock(LoginClient.class);
    private final AtomicBoolean ran = new AtomicBoolean();

    @Nested
    class Run {
        @Test
        void run_shouldRunTheHandler_whenTheStateAtRunTimeAcceptsIt() {
            when(client.getConnectionState()).thenReturn(ConnectionState.AUTHED_GG);

            lane().run();

            assertThat(ran).isTrue();
            verify(client, never()).dropped(any());
        }

        @Test
        void run_shouldCountWrongStateAndSkipTheHandler_whenTheStateAtRunTimeDoesNotAcceptIt() {
            when(client.getConnectionState()).thenReturn(ConnectionState.CONNECTED);

            lane().run();

            assertThat(ran).isFalse();
            verify(client).dropped(DropReason.WRONG_STATE);
        }

        @Test
        void run_shouldDiscardThePacketUncounted_whenTheClientIsClosing() {
            when(client.getConnectionState()).thenReturn(ConnectionState.AUTHED_GG);
            when(client.isClosing()).thenReturn(true);

            lane().run();

            assertThat(ran).isFalse();
            verify(client, never()).dropped(any());
        }

        private LanePacket lane() {
            return LanePacket.builder()
                    .client(client)
                    .handler(handled -> ran.set(handled == client))
                    .accepted(EnumSet.of(ConnectionState.AUTHED_GG))
                    .build();
        }
    }
}
