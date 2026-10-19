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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.ClanAccess;
import app.l2nx.highfive.network.serverpackets.ManagePledgePower;

public class RequestPledgePower extends ClientPacket {
    private int rank;
    private int action;
    private int privs;

    @Override
    protected void readImpl() {
        rank = readInt();
        action = readInt();
        if (action == 2) {
            privs = readInt();
        } else {
            privs = 0;
        }
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (action == 2) {
            if (player.isClanLeader()) {
                if (rank == 9) {
                    // The rights below cannot be bestowed upon Academy members:
                    // Join a clan or be dismissed.
                    // Title management, crest management, master management, level management,
                    // bulletin board administration
                    // Clan war, right to dismiss, set functions
                    // Auction, manage taxes, attack/defend registration, mercenary management
                    // => Leaves only ACCESS_WAREHOUSE, HALL_OPEN_DOOR, CASTLE_OPEN_DOOR?
                    privs &= ClanAccess.ACCESS_WAREHOUSE.getMask()
                            | ClanAccess.HALL_OPEN_DOOR.getMask()
                            | ClanAccess.CASTLE_OPEN_DOOR.getMask();
                }

                player.getClan().setRankPrivs(rank, privs);
            }
        } else {
            player.sendPacket(new ManagePledgePower(player.getClan(), action, rank));
        }
    }
}
