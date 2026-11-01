/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.entity.actor.Player;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoadedCharactersTest {
    private final LoadedCharacters loaded = new LoadedCharacters();
    private final UUID characterId = EntityIds.next();
    private final Player player = mock(Player.class);

    @Nested
    class Release {
        @Test
        void release_shouldForgetTheCharacter_whenItIsStillThisPlayer() {
            loaded.register(characterId, player);

            loaded.release(characterId, player);

            assertThat(loaded.find(characterId)).isEmpty();
        }

        @Test
        void release_shouldKeepALaterLoad_ofTheSameCharacter() {
            final Player reloaded = mock(Player.class);
            loaded.register(characterId, player);
            loaded.register(characterId, reloaded);

            loaded.release(characterId, player);

            assertThat(loaded.find(characterId)).containsSame(reloaded);
        }
    }
}
