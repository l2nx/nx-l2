/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.login.command;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.Session;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CharSelectionInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class ListCharactersHandlerTest {
    private final ListCharactersHandler handler = new ListCharactersHandler();
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);

    @Nested
    class Handle {
        @Test
        void handle_shouldSendTheCharacterList_andKeepItForTheSelection() {
            when(session.attachment(GameClient.class)).thenReturn(client);

            try (MockedConstruction<CharSelectionInfo> selections = mockConstruction(CharSelectionInfo.class)) {
                handler.handle(session, new ListCharacters());

                final CharSelectionInfo selection = selections.constructed().getFirst();
                verify(client).sendPacket(selection);
                verify(client).setCharSelection(selection.getCharInfo());
            }
        }
    }
}
