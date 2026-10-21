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

public class RadarControl extends ServerPacket {
    private final int showRadar;
    private final int type;
    private final int x;
    private final int y;
    private final int z;

    public RadarControl(int showRadar, int type, int x, int y, int z) {
        this.showRadar = showRadar; // showRader?? 0 = showradar; 1 = delete radar;
        this.type = type; // radar type??
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.RADAR_CONTROL.writeId(this, buffer);
        buffer.writeInt(showRadar);
        buffer.writeInt(type); // maybe type
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
    }
}
