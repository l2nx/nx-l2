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

public class AskJoinPledge extends ServerPacket {
    private final int requestorObjId;
    private final String subPledgeName;
    private final int pledgeType;
    private final String pledgeName;

    public AskJoinPledge(int requestorObjId, String subPledgeName, int pledgeType, String pledgeName) {
        this.requestorObjId = requestorObjId;
        this.subPledgeName = subPledgeName;
        this.pledgeType = pledgeType;
        this.pledgeName = pledgeName;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.ASK_JOIN_PLEDGE.writeId(this, buffer);
        buffer.writeInt(requestorObjId);
        if (subPledgeName != null) {
            buffer.writeString(pledgeType > 0 ? subPledgeName : pledgeName);
        }

        if (pledgeType != 0) {
            buffer.writeInt(pledgeType);
        }

        buffer.writeString(pledgeName);
    }
}
