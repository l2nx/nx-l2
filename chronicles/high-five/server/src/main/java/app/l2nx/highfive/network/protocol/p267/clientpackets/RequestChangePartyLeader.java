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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.network.SystemMessageId;

/**
 * This packet is received from client when a party leader requests to change the leadership to another player in his party.
 */
public class RequestChangePartyLeader extends ClientPacket {
    private String name;

    @Override
    protected void readImpl() {
        name = readString();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        final Party party = player.getParty();
        if (party != null) {
            final Player newLeader = party.getPlayerByName(name);
            if (party.isLeader(player)) {
                if ((newLeader != null) && party.containsPlayer(newLeader)) {
                    party.changePartyLeader(name);
                } else {
                    player.sendPacket(
                            SystemMessageId.YOU_MAY_ONLY_TRANSFER_PARTY_LEADERSHIP_TO_ANOTHER_MEMBER_OF_THE_PARTY);
                }
            } else if ((newLeader != null) && party.containsPlayer(newLeader)) {
                player.sendPacket(
                        SystemMessageId.ONLY_THE_LEADER_OF_THE_PARTY_CAN_TRANSFER_PARTY_LEADERSHIP_TO_ANOTHER_PLAYER);
            }
        }
    }
}
