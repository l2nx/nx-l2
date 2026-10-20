/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.SessionKey;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.LoginFail;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

// LoginService binds a static bridge.
@ResourceLock("LoginService")
class LinkLostHandlerTest {
    private static final SessionKey KEY =
            SessionKey.builder().loginOk1(1).loginOk2(2).playOk1(3).playOk2(4).build();

    private final FakeLoginPort port = new FakeLoginPort();
    private final OnlineAccounts accounts = new OnlineAccounts();
    private final LoginService loginService = new LoginService(port, accounts, new ServerListing());
    private final LinkLostHandler handler = new LinkLostHandler(loginService, accounts);

    @Test
    void accept_shouldCloseEveryClientWaitingForLogin_andFreeItsLogin() {
        final GameClient alice = mock(GameClient.class);
        final GameClient bob = mock(GameClient.class);
        accounts.reserve("alice", alice);
        accounts.awaitAuthentication("alice", alice, KEY);
        accounts.reserve("bob", bob);
        accounts.awaitAuthentication("bob", bob, KEY);

        handler.accept(new LinkLost());

        verify(alice).close(any(LoginFail.class));
        verify(bob).close(any(LoginFail.class));
        assertThat(accounts.takePending("alice")).isNull();
        assertThat(accounts.reserve("alice", alice)).isTrue();
        assertThat(accounts.reserve("bob", bob)).isTrue();
        assertThat(port.getCalls()).isEmpty();
    }

    @Test
    void accept_shouldTellEveryPlayerWaitingForAPasswordChange_thatItFailed() {
        final Player alice = mock(Player.class);
        accounts.awaitPasswordChange("Alice");
        accounts.awaitPasswordChange("Bob");

        try (MockedStatic<World> world = mockStatic(World.class)) {
            world.when(() -> World.getPlayer("Alice")).thenReturn(alice);

            handler.accept(new LinkLost());

            world.verify(() -> World.getPlayer("Bob"));
        }

        verify(alice).sendMessage(LoginService.PASSWORD_CHANGE_FAILED);
        assertThat(accounts.takeAllPasswordChanges()).isEmpty();
    }

    @Test
    void accept_shouldKeepTheAuthenticatedSessions() {
        final GameClient player = mock(GameClient.class);
        final UUID accountId = UUID.randomUUID();
        accounts.reserve("alice", player);
        accounts.enter(accountId, player);

        handler.accept(new LinkLost());

        verify(player, never()).close(any(LoginFail.class));
        assertThat(accounts.find(accountId)).isSameAs(player);
    }
}
