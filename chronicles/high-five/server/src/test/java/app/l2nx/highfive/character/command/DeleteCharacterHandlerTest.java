/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.character.CharacterService;
import app.l2nx.highfive.character.DeleteFailure;
import app.l2nx.highfive.character.view.CharacterDeleteFailed;
import app.l2nx.highfive.character.view.CharacterDeleted;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.GameContexts;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeleteCharacterHandlerTest {
    private static final int SLOT = 1;
    private static final int FIRST_SLOT = 0;

    private final CharacterService characters = mock(CharacterService.class);
    private final DeleteCharacterHandler handler = new DeleteCharacterHandler(characters);
    private final GameClient client = mock(GameClient.class);
    private final ClientPort port = mock(ClientPort.class);

    DeleteCharacterHandlerTest() {
        when(client.port()).thenReturn(port);
    }

    @Nested
    class Context {
        @Test
        void context_shouldBeTheLobby() {
            assertThat(handler.context()).isSameAs(GameContexts.LOBBY);
        }
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldConfirmAndShowTheListFromTheFirstSlot_whenDeleted() {
            when(client.markToDeleteChar(SLOT)).thenReturn((byte) 0);

            handler.handle(client, new DeleteCharacter(SLOT));

            verify(port).emit(CharacterDeleted.INSTANCE);
            verify(characters).showCharacterList(client, FIRST_SLOT);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"1, CLAN_MEMBER", "2, CLAN_LEADER", "3, LOADED"})
        void handle_shouldRefuseAndStillShowTheList_whenTheCharacterIsHeld(byte result, DeleteFailure failure) {
            when(client.markToDeleteChar(SLOT)).thenReturn(result);

            handler.handle(client, new DeleteCharacter(SLOT));

            verify(port).emit(new CharacterDeleteFailed(failure));
            verify(characters).showCharacterList(client, FIRST_SLOT);
        }

        @Test
        void handle_shouldStillShowTheList_whenTheDeletionThrows() {
            when(client.markToDeleteChar(SLOT)).thenThrow(new IllegalStateException("database down"));

            handler.handle(client, new DeleteCharacter(SLOT));

            verify(characters).showCharacterList(client, FIRST_SLOT);
        }
    }
}
