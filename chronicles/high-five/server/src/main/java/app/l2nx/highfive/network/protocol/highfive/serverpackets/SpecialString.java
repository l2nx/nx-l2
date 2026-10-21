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

public class SpecialString extends ServerPacket {
    private final int strId;
    private final int fontSize;
    private final int x;
    private final int y;
    private final int color;
    private final boolean draw;
    private final String text;

    public SpecialString(int strId, boolean isDraw, int fontSize, int x, int y, int color, String text) {
        this.strId = strId;
        draw = isDraw;
        this.fontSize = fontSize;
        this.x = x;
        this.y = y;
        this.color = color;
        this.text = text;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.SERVER_CLOSE_SOCKET.writeId(this, buffer);
        buffer.writeByte(strId); // string ID
        buffer.writeByte(draw); // 1 - draw / 0 - hide
        buffer.writeByte(fontSize); // -1 to 3 (font size)
        buffer.writeInt(x); // ClientRight - x
        buffer.writeInt(y); // ClientTop + y
        buffer.writeInt(color); // AARRGGBB
        buffer.writeString(text); // wide string max len = 63
    }
}
