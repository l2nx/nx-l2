/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
package app.l2nx.highfive.login.command;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.OnlineAccounts.PendingAuthentication;
import java.util.Map;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class LinkLostHandler implements Consumer<LinkLost> {
    private final LoginService loginService;
    private final OnlineAccounts accounts;

    @Override
    public void accept(LinkLost command) {
        final Map<String, PendingAuthentication> pending = accounts.takeAllPending();
        if (!pending.isEmpty()) {
            log.warn("The link to the login server ended, closing {} connections waiting for login", pending.size());
        }
        pending.forEach((login, waiting) -> loginService.failAuthentication(login, waiting.client()));

        for (String characterName : accounts.takeAllPasswordChanges()) {
            final Player player = World.getPlayer(characterName);
            if (player != null) {
                player.sendMessage(LoginService.PASSWORD_CHANGE_FAILED);
            }
        }
    }
}
