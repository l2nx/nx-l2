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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class SetupGauge extends ServerPacket {
    public static final int BLUE = 0;
    public static final int RED = 1;
    public static final int CYAN = 2;

    private final int objectId;
    private final int dat1;
    private final int time;
    private final int time2;

    public SetupGauge(int charObjId, int dat1, int time) {
        objectId = charObjId;
        this.dat1 = dat1; // color 0-blue 1-red 2-cyan 3-green
        this.time = time;
        time2 = time;
    }

    public SetupGauge(int charObjId, int color, int currentTime, int maxTime) {
        objectId = charObjId;
        dat1 = color; // color 0-blue 1-red 2-cyan 3-green
        time = currentTime;
        time2 = maxTime;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.SETUP_GAUGE.writeId(this, buffer);
        buffer.writeInt(objectId);
        buffer.writeInt(dat1);
        buffer.writeInt(time);
        buffer.writeInt(time2);
    }
}
