/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.GameClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GameContextTest {
    @ParameterizedTest(name = "{0} with account={1}, character={2}, inWorld={3}: {4}")
    @CsvSource({
        "LOGIN,false,false,false,true",
        "LOGIN,true,false,false,false",
        "ACCOUNT,false,false,false,false",
        "ACCOUNT,true,true,true,true",
        "LOBBY,true,false,false,true",
        "LOBBY,true,true,false,false",
        "CHARACTER,true,false,false,false",
        "CHARACTER,true,true,false,true",
        "CHARACTER,true,true,true,true",
        "WORLD,true,true,false,false",
        "WORLD,true,true,true,true"
    })
    void presentFor_shouldReadTheClientsGameState(
            GameContext context, boolean account, boolean character, boolean inWorld, boolean expected) {
        final GameClient client = mock(GameClient.class);
        when(client.getAccountName()).thenReturn(account ? "alice" : null);
        if (character) {
            final Player player = mock(Player.class);
            when(player.hasEnteredWorld()).thenReturn(inWorld);
            when(client.getPlayer()).thenReturn(player);
        }

        assertThat(context.presentFor(client)).isEqualTo(expected);
    }
}
