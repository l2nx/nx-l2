/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.session.GameContexts;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoveBlockedHandlerTest {
    private final Player player = mock(Player.class);
    private final MoveBlockedHandler handler = new MoveBlockedHandler();

    @Nested
    class Context {
        @Test
        void context_shouldBeThePlayerInTheWorld() {
            assertThat(handler.context()).isSameAs(GameContexts.WORLD);
        }
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldTellTheAiWhereThePlayerWasBlocked() {
            final CreatureAI ai = mock(CreatureAI.class);
            when(player.getAI()).thenReturn(ai);

            handler.handle(
                    player, MoveBlocked.builder().x(5).y(6).z(7).heading(8).build());

            verify(ai).notifyActionArrivedBlocked(new Location(5, 6, 7, 8));
        }
    }
}
