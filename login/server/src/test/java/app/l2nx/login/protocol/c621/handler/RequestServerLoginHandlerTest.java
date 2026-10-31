/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.session.LoginController;
import app.l2nx.login.session.model.SessionKey;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerLogin;
import app.l2nx.protocol.login.c621.serverpackets.PlayOk;
import app.l2nx.protocol.login.c621.serverpackets.reason.LoginFailReason;
import app.l2nx.protocol.login.c621.serverpackets.reason.PlayFailReason;
import java.io.File;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class RequestServerLoginHandlerTest {
    private static final SessionKey KEY =
            SessionKey.builder().loginOk1(1).loginOk2(2).playOk1(3).playOk2(4).build();

    private final LoginController controller = mock(LoginController.class);
    private final LoginClient client = mock(LoginClient.class);

    RequestServerLoginHandlerTest() {
        when(client.getSessionKey()).thenReturn(KEY);
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldBindTheKeyToTheSelectedServer_beforeSendingPlayOk() {
            when(controller.isLoginPossible(client, 2)).thenReturn(true);

            handler(true).handle(client, request(1, 2, 2));

            final InOrder order = inOrder(client);
            order.verify(client).setJoinedServerId(2);
            order.verify(client)
                    .sendPacket(PlayOk.builder().playOk1(3).playOk2(4).build());
        }

        @Test
        void handle_shouldCloseWithAccessFailed_whenTheLoginPairIsWrongAndTheLicenceWasShown() {
            handler(true).handle(client, request(9, 2, 1));

            verify(client).close(LoginFailReason.REASON_ACCESS_FAILED);
            verify(controller, never()).isLoginPossible(any(), anyInt());
            verify(client, never()).setJoinedServerId(any());
        }

        @ParameterizedTest(name = "show licence {0}")
        @ValueSource(booleans = {true, false})
        void handle_shouldCloseWithServerOverloaded_whenTheServerRefusesTheClient(boolean showLicence) {
            handler(showLicence).handle(client, request(1, 2, 1));

            verify(client).close(PlayFailReason.REASON_SERVER_OVERLOADED);
            verify(client, never()).setJoinedServerId(any());
        }

        @Test
        void handle_shouldIgnoreTheLoginPair_whenTheLicenceWasNotShown() {
            when(controller.isLoginPossible(client, 1)).thenReturn(true);

            handler(false).handle(client, request(9, 9, 1));

            verify(client).sendPacket(PlayOk.builder().playOk1(3).playOk2(4).build());
        }
    }

    private RequestServerLoginHandler handler(boolean showLicence) {
        return new RequestServerLoginHandler(
                controller,
                TestLoginProperties.create(new File(".")).toBuilder()
                        .showLicence(showLicence)
                        .build());
    }

    private static RequestServerLogin request(int loginOk1, int loginOk2, int serverId) {
        return RequestServerLogin.builder()
                .loginOk1(loginOk1)
                .loginOk2(loginOk2)
                .serverId(serverId)
                .build();
    }
}
