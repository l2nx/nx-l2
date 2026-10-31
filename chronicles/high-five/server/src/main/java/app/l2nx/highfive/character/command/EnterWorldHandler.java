/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.command;

import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.PlayerWorldEntry;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.GameContexts;
import org.springframework.stereotype.Component;

@Component
class EnterWorldHandler implements ContextCommandHandler<Player, EnterWorld> {
    @Override
    public ContextResolver<Player> context() {
        return GameContexts.CHARACTER;
    }

    @Override
    public void handle(Player player, EnterWorld command) {
        final GameClient client = player.getClient();
        try {
            PlayerWorldEntry.enter(player, command.route());
        } catch (RuntimeException e) {
            client.closeNow();
            throw e;
        }
    }
}
