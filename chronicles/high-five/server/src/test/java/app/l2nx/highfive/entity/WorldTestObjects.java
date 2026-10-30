/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Player;

public final class WorldTestObjects {
    private WorldTestObjects() {}

    public static Player player(int objectId) {
        final Player player = mock(Player.class);
        when(player.getObjectId()).thenReturn(objectId);
        when(player.isPlayer()).thenReturn(true);
        when(player.isPlayable()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.asPlayer()).thenReturn(player);
        return player;
    }
}
