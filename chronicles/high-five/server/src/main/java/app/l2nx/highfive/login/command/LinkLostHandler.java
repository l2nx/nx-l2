/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
package app.l2nx.highfive.login.command;

import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.ServiceCommandHandler;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.system.OnlineAccounts;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class LinkLostHandler implements ServiceCommandHandler<LinkLost> {
    private final OnlineAccounts accounts;
    // The dispatcher's registry is built from this handler, so the dispatcher resolves on first use.
    private final ObjectProvider<CommandDispatcher> commands;

    @Override
    public void handle(LinkLost command) {
        final List<UUID> authentications = accounts.pendingAuthentications();
        if (!authentications.isEmpty()) {
            log.warn(
                    "The link to the login server ended, closing {} connections waiting for login",
                    authentications.size());
        }
        final CommandDispatcher dispatcher = commands.getObject();
        for (UUID requestId : authentications) {
            dispatcher.submit(AuthenticationResult.builder()
                    .requestId(requestId)
                    .accepted(false)
                    .build());
        }
        for (UUID requestId : accounts.pendingPasswordChanges()) {
            dispatcher.submit(PasswordChangeResult.builder()
                    .requestId(requestId)
                    .message(LoginService.PASSWORD_CHANGE_FAILED)
                    .build());
        }
    }
}
