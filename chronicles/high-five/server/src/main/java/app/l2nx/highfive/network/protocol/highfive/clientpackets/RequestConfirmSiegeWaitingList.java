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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SiegeDefenderList;

/**
 * @version $Revision: 1.3.4.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestConfirmSiegeWaitingList extends ClientPacket {
    private int approved;
    private int castleId;
    private int clanId;

    @Override
    protected void readImpl() {
        castleId = readInt();
        clanId = readInt();
        approved = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        // Check if the player has a clan.
        if (player.getClan() == null) {
            return;
        }

        final Castle castle = CastleManager.getInstance().getCastleById(castleId);
        if (castle == null) {
            return;
        }

        // Check if leader of the clan who owns the castle?
        if ((castle.getOwnerId() != player.getClanId()) || (!player.isClanLeader())) {
            return;
        }

        final Clan clan = ClanTable.getInstance().getClan(clanId);
        if (clan == null) {
            return;
        }

        if (!castle.getSiege().isRegistrationOver()) {
            if (approved == 1) {
                if (castle.getSiege().checkIsDefenderWaiting(clan)) {
                    castle.getSiege().approveSiegeDefenderClan(clanId);
                } else {
                    return;
                }
            } else {
                if ((castle.getSiege().checkIsDefenderWaiting(clan))
                        || (castle.getSiege().checkIsDefender(clan))) {
                    castle.getSiege().removeSiegeClan(clanId);
                }
            }
        }

        // Update the defender list.
        player.sendPacket(new SiegeDefenderList(castle));
    }
}
