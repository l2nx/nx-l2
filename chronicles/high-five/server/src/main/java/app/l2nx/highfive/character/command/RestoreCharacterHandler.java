/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.character.command;

import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.highfive.character.CharacterService;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerRestore;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.GameContexts;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class RestoreCharacterHandler implements ContextCommandHandler<GameClient, RestoreCharacter> {
    private static final int FIRST_SLOT = 0;

    private final CharacterService characters;

    @Override
    public ContextResolver<GameClient> context() {
        return GameContexts.LOBBY;
    }

    @Override
    public void handle(GameClient client, RestoreCharacter command) {
        if (!client.getFloodProtectors().canSelectCharacter()) {
            return;
        }

        client.restore(command.slot());
        characters.showCharacterList(client, FIRST_SLOT);

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_RESTORE)) {
            final CharacterSummary info = client.getCharSelection(command.slot());
            EventDispatcher.getInstance()
                    .notifyEvent(OnPlayerRestore.builder()
                            .objectId(info.objectId())
                            .name(info.name())
                            .client(client)
                            .build());
        }
    }
}
