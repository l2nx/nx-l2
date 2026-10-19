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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.Shortcut;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;
import java.util.Collection;

public class ShortcutInit extends ServerPacket {
    private Collection<Shortcut> shortcuts;

    public ShortcutInit(Player player) {
        if (player == null) {
            return;
        }

        shortcuts = player.getAllShortcuts();
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.SHORT_CUT_INIT.writeId(this, buffer);
        buffer.writeInt(shortcuts.size());
        for (Shortcut sc : shortcuts) {
            buffer.writeInt(sc.getType().ordinal());
            buffer.writeInt(sc.getSlot() + (sc.getPage() * 12));
            switch (sc.getType()) {
                case ITEM: {
                    buffer.writeInt(sc.getId());
                    buffer.writeInt(1);
                    buffer.writeInt(sc.getSharedReuseGroup());
                    buffer.writeInt(0);
                    buffer.writeInt(0);
                    buffer.writeShort(0);
                    buffer.writeShort(0);
                    break;
                }
                case SKILL: {
                    buffer.writeInt(sc.getId());
                    buffer.writeInt(sc.getLevel());
                    buffer.writeByte(0); // C5
                    buffer.writeInt(1); // C6
                    break;
                }
                case ACTION:
                case MACRO:
                case RECIPE:
                case BOOKMARK: {
                    buffer.writeInt(sc.getId());
                    buffer.writeInt(1); // C6
                }
            }
        }
    }
}
