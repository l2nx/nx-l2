/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.command.SessionAddress;
import app.l2nx.engine.core.session.command.SessionDirectory;
import app.l2nx.highfive.login.LoginService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameSessionDirectory implements SessionDirectory {
    private final LoginService loginService;

    @Override
    public Optional<Session> find(SessionAddress address) {
        final GameClient client = switch (address) {
            case SessionAddress.Account account -> loginService.findClient(account.accountId());
            case SessionAddress.Request request -> loginService.findRequester(request.requestId());
        };
        // A detached client belongs to an offline trader or player: its connection, and with it the session, is gone.
        if ((client == null) || client.isDetached()) {
            return Optional.empty();
        }
        return Optional.of(client.session());
    }
}
