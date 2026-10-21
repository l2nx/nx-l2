/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.LoginServer;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.controller.AccountInfo;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.controller.LoginResult;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.network.LoginClient;
import app.l2nx.protocol.c621.clientpackets.RequestAuthLogin;
import app.l2nx.protocol.c621.crypt.Credentials;
import app.l2nx.protocol.c621.crypt.Credentials.Layout;
import app.l2nx.protocol.c621.serverpackets.reason.LoginFailReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class RequestAuthLoginHandlerTest {
    private static final KeyPair KEYS = keyPair();
    private static final AccountInfo ACCOUNT = new AccountInfo(UUID.randomUUID(), "test1", "hash", 0, 1);

    private final LoginClient client = client();
    private final LoginController controller = mock(LoginController.class);

    @Nested
    class Handle {
        @ParameterizedTest(name = "block of {0} bytes")
        @ValueSource(ints = {128, 256})
        void handle_shouldCloseWithAccessFailed_whenTheBlockCannotBeDecrypted(int length) {
            final byte[] larger = new byte[length];
            Arrays.fill(larger, (byte) 0xFF);
            final ByteBuf body = Unpooled.wrappedBuffer(larger);
            final RequestAuthLogin packet;
            try {
                packet = RequestAuthLogin.decode(body);
            } finally {
                body.release();
            }

            RequestAuthLoginHandler.handle(client, packet);

            verify(client).close(LoginFailReason.REASON_ACCESS_FAILED);
        }

        @ParameterizedTest(name = "user {0}, password {1}")
        @CsvSource({", secret", "test1, ", ","})
        void handle_shouldCloseWithUserOrPassWrong_whenAFieldIsEmpty(String user, String password) throws Exception {
            final RequestAuthLogin packet = Credentials.builder()
                    .user(user)
                    .password(password)
                    .build()
                    .encrypt(KEYS.getPublic(), Layout.ONE_BLOCK);

            try (MockedStatic<LoginController> ignored = controllerInstance()) {
                RequestAuthLoginHandler.handle(client, packet);
            }

            verify(client).close(LoginFailReason.REASON_USER_OR_PASS_WRONG);
            verify(controller, never()).retriveAccountInfo(anyString(), any(), any());
        }

        @Test
        void handle_shouldCloseWithAccountInUse_whenTheRetriedCheckInStillFindsNoGameServer() throws Exception {
            alreadyOnGameServer();
            when(controller.getAccountOnGameServer(ACCOUNT.getId())).thenReturn(null);

            try (MockedStatic<LoginController> ignored = controllerInstance()) {
                RequestAuthLoginHandler.handle(client, validLogin());
            }

            verify(controller, times(2)).tryCheckinAccount(client, "203.0.113.7", ACCOUNT);
            verify(client).close(LoginFailReason.REASON_ACCOUNT_IN_USE);
        }

        @Test
        void handle_shouldLogIn_whenTheAccountLeftItsGameServerBeforeTheRetriedCheckIn() throws Exception {
            when(controller.retriveAccountInfo("203.0.113.7", "test1", "secret"))
                    .thenReturn(ACCOUNT);
            when(controller.tryCheckinAccount(client, "203.0.113.7", ACCOUNT))
                    .thenReturn(LoginResult.ALREADY_ON_GS, LoginResult.AUTH_SUCCESS);
            when(controller.getAccountOnGameServer(ACCOUNT.getId())).thenReturn(null);
            final LoginServer server = mock(LoginServer.class);
            final LoginProperties properties = mock(LoginProperties.class);
            when(server.getProperties()).thenReturn(properties);

            try (MockedStatic<LoginController> ignored = controllerInstance();
                    MockedStatic<LoginServer> serverInstance = mockStatic(LoginServer.class)) {
                serverInstance.when(LoginServer::getInstance).thenReturn(server);
                RequestAuthLoginHandler.handle(client, validLogin());
            }

            verify(client).setAccount("test1");
            verify(client).sendServerList();
            verify(client, never()).close(any(LoginFailReason.class));
        }

        @Test
        void handle_shouldCloseAndKickFromTheGameServer_whenTheAccountIsOnIt() throws Exception {
            alreadyOnGameServer();
            final GameServerInfo server = mock(GameServerInfo.class);
            final GameServerLink link = mock(GameServerLink.class);
            when(server.getGameServerLink()).thenReturn(link);
            when(controller.getAccountOnGameServer(ACCOUNT.getId())).thenReturn(server);

            try (MockedStatic<LoginController> ignored = controllerInstance()) {
                RequestAuthLoginHandler.handle(client, validLogin());
            }

            verify(client).close(LoginFailReason.REASON_ACCOUNT_IN_USE);
            verify(link).kickPlayer(ACCOUNT.getId());
        }
    }

    private void alreadyOnGameServer() {
        when(controller.retriveAccountInfo("203.0.113.7", "test1", "secret")).thenReturn(ACCOUNT);
        when(controller.tryCheckinAccount(client, "203.0.113.7", ACCOUNT)).thenReturn(LoginResult.ALREADY_ON_GS);
    }

    private MockedStatic<LoginController> controllerInstance() {
        final MockedStatic<LoginController> instance = mockStatic(LoginController.class);
        instance.when(LoginController::getInstance).thenReturn(controller);
        return instance;
    }

    private static RequestAuthLogin validLogin() throws Exception {
        return Credentials.builder()
                .user("test1")
                .password("secret")
                .build()
                .encrypt(KEYS.getPublic(), Layout.ONE_BLOCK);
    }

    private static LoginClient client() {
        final LoginClient client = mock(LoginClient.class);
        when(client.getRSAPrivateKey()).thenReturn((RSAPrivateKey) KEYS.getPrivate());
        when(client.getIp()).thenReturn("203.0.113.7");
        return client;
    }

    private static KeyPair keyPair() {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
