/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.highfive;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;

/**
 * Game state a legacy client packet needs, checked when it executes; a packet without it is rejected.
 * TODO: remove with the legacy routes; ported commands resolve their own context.
 */
public enum LegacyContext {
    /** Read before authentication; runs only while no account is authenticated. */
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
