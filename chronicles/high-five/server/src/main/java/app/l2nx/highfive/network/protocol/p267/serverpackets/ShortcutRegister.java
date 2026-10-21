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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.entity.actor.holders.player.Shortcut;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class ShortcutRegister extends ServerPacket {
    private final Shortcut shortcut;

    /**
     * Register new skill shortcut
     * @param shortcut
     */
    public ShortcutRegister(Shortcut shortcut) {
        this.shortcut = shortcut;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.SHORT_CUT_REGISTER.writeId(this, buffer);
        buffer.writeInt(shortcut.getType().ordinal());
        buffer.writeInt(shortcut.getSlot() + (shortcut.getPage() * 12)); // C4 Client
        switch (shortcut.getType()) {
            case ITEM: {
                buffer.writeInt(shortcut.getId());
                buffer.writeInt(shortcut.getCharacterType());
                buffer.writeInt(shortcut.getSharedReuseGroup());
                buffer.writeInt(0); // unknown
                buffer.writeInt(0); // unknown
                buffer.writeInt(0); // item augment id
                break;
            }
            case SKILL: {
                buffer.writeInt(shortcut.getId());
                buffer.writeInt(shortcut.getLevel());
                buffer.writeByte(0); // C5
                buffer.writeInt(shortcut.getCharacterType());
                break;
            }
            case ACTION:
            case MACRO:
            case RECIPE:
            case BOOKMARK: {
                buffer.writeInt(shortcut.getId());
                buffer.writeInt(shortcut.getCharacterType());
            }
        }
    }
}
