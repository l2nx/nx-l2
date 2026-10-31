/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.session;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;

public enum GameContext {
    LOGIN,
    ACCOUNT,
    LOBBY,
    CHARACTER,
    WORLD;

    public boolean presentFor(GameClient client) {
        final Player player = client.getPlayer();
        return switch (this) {
            case LOGIN -> client.getAccountName() == null;
            case ACCOUNT -> client.getAccountName() != null;
            case LOBBY -> (client.getAccountName() != null) && (player == null);
            case CHARACTER -> player != null;
            case WORLD -> (player != null) && player.hasEnteredWorld();
        };
    }
}
