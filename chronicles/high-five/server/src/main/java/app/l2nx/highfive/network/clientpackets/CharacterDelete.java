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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.mechanics.events.Containers;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerDelete;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.holders.CharacterInfoHolder;
import app.l2nx.highfive.network.serverpackets.CharDeleteFail;
import app.l2nx.highfive.network.serverpackets.CharDeleteSuccess;
import app.l2nx.highfive.network.serverpackets.CharSelectionInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * @version $Revision: 1.8.2.1.2.3 $ $Date: 2005/03/27 15:29:30 $
 */
@Slf4j
public class CharacterDelete extends ClientPacket {
    // cd
    private int charSlot;

    @Override
    protected void readImpl() {
        charSlot = readInt();
    }

    @Override
    protected void runImpl() {
        final GameClient client = getClient();

        // if (!client.getFloodProtectors().canSelectCharacter())
        // {
        // sendPacket(new CharDeleteFail(CharDeleteFail.REASON_DELETION_FAILED));
        // return;
        // }

        try {
            switch (client.markToDeleteChar(charSlot)) {
                default:
                case -1: // Error
                {
                    break;
                }
                case 0: // Success!
                {
                    client.sendPacket(new CharDeleteSuccess());
                    if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_DELETE, Containers.Players())) {
                        final CharacterInfoHolder charInfo = client.getCharSelection(charSlot);
                        EventDispatcher.getInstance()
                                .notifyEvent(
                                        OnPlayerDelete.builder()
                                                .objectId(charInfo.getObjectId())
                                                .name(charInfo.getName())
                                                .client(client)
                                                .build(),
                                        Containers.Players());
                    }
                    break;
                }
                case 1: {
                    client.sendPacket(new CharDeleteFail(CharDeleteFail.REASON_YOU_MAY_NOT_DELETE_CLAN_MEMBER));
                    break;
                }
                case 2: {
                    client.sendPacket(new CharDeleteFail(CharDeleteFail.REASON_CLAN_LEADERS_MAY_NOT_BE_DELETED));
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("{}: {}", getClass().getSimpleName(), e.getMessage());
        }

        final CharSelectionInfo cl = new CharSelectionInfo(client.getAccountName(), client.getSessionId().playOkID1, 0);
        client.sendPacket(cl);
        client.setCharSelection(cl.getCharInfo());
    }
}
