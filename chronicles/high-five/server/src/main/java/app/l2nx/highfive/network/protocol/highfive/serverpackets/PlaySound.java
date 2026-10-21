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

import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import lombok.Getter;

public class PlaySound extends ServerPacket {
    private final int unknown1;

    @Getter
    private final String soundName;

    private final int unknown3;
    private final int unknown4;
    private final int unknown5;
    private final int unknown6;
    private final int unknown7;
    private final int unknown8;

    public PlaySound(String soundFile) {
        unknown1 = 0;
        soundName = soundFile;
        unknown3 = 0;
        unknown4 = 0;
        unknown5 = 0;
        unknown6 = 0;
        unknown7 = 0;
        unknown8 = 0;
    }

    public PlaySound(
            int unknown1, String soundFile, int unknown3, int unknown4, int unknown5, int unknown6, int unknown7) {
        this.unknown1 = unknown1;
        soundName = soundFile;
        this.unknown3 = unknown3;
        this.unknown4 = unknown4;
        this.unknown5 = unknown5;
        this.unknown6 = unknown6;
        this.unknown7 = unknown7;
        unknown8 = 0;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PLAY_SOUND.writeId(this, buffer);
        buffer.writeInt(unknown1); // unknown 0 for quest and ship;
        buffer.writeString(soundName);
        buffer.writeInt(unknown3); // unknown 0 for quest; 1 for ship;
        buffer.writeInt(unknown4); // 0 for quest; objectId of ship
        buffer.writeInt(unknown5); // x
        buffer.writeInt(unknown6); // y
        buffer.writeInt(unknown7); // z
        buffer.writeInt(unknown8);
    }
}
