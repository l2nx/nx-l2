/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.login.system.OnlineAccounts;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PasswordChangeResultHandlerTest {
    private final OnlineAccounts accounts = new OnlineAccounts();
    private final PasswordChangeResultHandler handler = new PasswordChangeResultHandler(accounts);
    private final Player player = mock(Player.class);

    @Test
    void accept_shouldTellThePlayer_whenOnline() {
        try (MockedStatic<World> world = mockStatic(World.class)) {
            world.when(() -> World.getPlayer("Alice")).thenReturn(player);

            handler.accept(PasswordChangeResult.builder()
                    .characterName("Alice")
                    .message("Password changed.")
                    .build());
        }

        verify(player).sendMessage("Password changed.");
    }

    @Test
    void accept_shouldEndTheWaitForTheAnswer() {
        accounts.awaitPasswordChange("Alice");

        try (MockedStatic<World> world = mockStatic(World.class)) {
            handler.accept(PasswordChangeResult.builder()
                    .characterName("Alice")
                    .message("Password changed.")
                    .build());
        }

        assertThat(accounts.takeAllPasswordChanges()).isEmpty();
    }

    @Test
    void accept_shouldTellNobody_whenThePlayerIsOffline() {
        try (MockedStatic<World> world = mockStatic(World.class)) {
            handler.accept(PasswordChangeResult.builder()
                    .characterName("Bob")
                    .message("Password changed.")
                    .build());

            world.verify(() -> World.getPlayer("Bob"));
        }

        verifyNoInteractions(player);
    }
}
