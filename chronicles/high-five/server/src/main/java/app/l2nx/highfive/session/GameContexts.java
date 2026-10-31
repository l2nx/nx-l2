/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.session;

import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;

public final class GameContexts {
    public static final ContextResolver<GameClient> LOGIN = new ClientContext(GameContext.LOGIN);

    public static final ContextResolver<GameClient> LOBBY = new ClientContext(GameContext.LOBBY);

    public static final ContextResolver<Player> CHARACTER = new PlayerContext(GameContext.CHARACTER);

    public static final ContextResolver<Player> WORLD = new PlayerContext(GameContext.WORLD);

    private GameContexts() {}

    private record ClientContext(GameContext context) implements ContextResolver<GameClient> {
        @Override
        public String name() {
            return context.name();
        }

        @Override
        public GameClient resolve(Session session) {
            final GameClient client = GameClient.of(session);
            return context.presentFor(client) ? client : null;
        }
    }

    private record PlayerContext(GameContext context) implements ContextResolver<Player> {
        @Override
        public String name() {
            return context.name();
        }

        @Override
        public Player resolve(Session session) {
            final GameClient client = GameClient.of(session);
            return context.presentFor(client) ? client.getPlayer() : null;
        }
    }
}
