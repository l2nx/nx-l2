/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.command;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.Session;
import app.l2nx.highfive.character.CharacterService;
import app.l2nx.highfive.network.GameClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ListCharactersHandlerTest {
    private final CharacterService characters = mock(CharacterService.class);
    private final ListCharactersHandler handler = new ListCharactersHandler(characters);
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);

    @Nested
    class Handle {
        @Test
        void handle_shouldShowTheCharacterListThroughTheService() {
            when(session.attachment(GameClient.class)).thenReturn(client);

            handler.handle(session, new ListCharacters());

            verify(characters).showCharacterList(client);
        }
    }
}
