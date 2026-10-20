/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.FakeLoginPort.Call;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedConstruction;

// LoginService binds a static bridge.
@ResourceLock("LoginService")
class KickAccountHandlerTest {
    private final FakeLoginPort port = new FakeLoginPort();
    private final OnlineAccounts accounts = new OnlineAccounts();
    private final LoginService loginService = new LoginService(port, accounts, new ServerListing());
    private final KickAccountHandler handler = new KickAccountHandler(loginService, accounts);

    @Test
    void accept_shouldOnlyFreeTheAccount_whenNoSessionHoldsIt() {
        final UUID accountId = UUID.randomUUID();

        handler.accept(new KickAccount(accountId));

        assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
    }

    @Test
    void accept_shouldStoreAndRemoveThePlayer_whenTheSessionIsDetached() {
        final UUID accountId = UUID.randomUUID();
        final GameClient client = mock(GameClient.class);
        final Player player = mock(Player.class);
        when(client.isDetached()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getAccountName()).thenReturn("alice");
        accounts.reserve("alice", client);
        accounts.enter(accountId, client);

        try (MockedConstruction<SystemMessage> messages = mockConstruction(SystemMessage.class)) {
            handler.accept(new KickAccount(accountId));

            verify(client).close(messages.constructed().getFirst());
        }

        verify(player).storeMe();
        verify(player).deleteMe();
        verify(client).disconnect();
        assertThat(port.getCalls()).containsExactly(Call.of("accountLeft", accountId));
    }
}
