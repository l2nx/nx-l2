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

import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

public class PledgeShowInfoUpdate extends ServerPacket {
    private final Clan clan;

    public PledgeShowInfoUpdate(Clan clan) {
        this.clan = clan;
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.PLEDGE_SHOW_INFO_UPDATE.writeId(this, buffer);

        // sending empty data so client will ask all the info in response ;)
        buffer.writeInt(clan.getId());
        buffer.writeInt(clan.getCrestId());
        buffer.writeInt(clan.getLevel()); // clan level
        buffer.writeInt(clan.getCastleId());
        buffer.writeInt(clan.getHideoutId());
        buffer.writeInt(clan.getFortId());
        buffer.writeInt(clan.getRank());
        buffer.writeInt(clan.getReputationScore()); // clan reputation score
        buffer.writeInt(0); // ?
        buffer.writeInt(0); // ?
        buffer.writeInt(clan.getAllyId());
        buffer.writeString(clan.getAllyName()); // c5
        buffer.writeInt(clan.getAllyCrestId()); // c5
        buffer.writeInt(clan.isAtWar()); // c5
    }
}
