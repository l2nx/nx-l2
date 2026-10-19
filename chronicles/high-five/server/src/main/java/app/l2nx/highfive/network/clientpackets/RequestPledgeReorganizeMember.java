/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanAccess;
import app.l2nx.highfive.entity.clan.ClanMember;

/**
 * Format: (ch) dSdS
 * @author -Wooden-
 */
public class RequestPledgeReorganizeMember extends ClientPacket {
    private int isMemberSelected;
    private String memberName;
    private int newPledgeType;
    private String selectedMember;

    @Override
    protected void readImpl() {
        isMemberSelected = readInt();
        memberName = readString();
        newPledgeType = readInt();
        selectedMember = readString();
    }

    @Override
    protected void runImpl() {
        if (isMemberSelected == 0) {
            return;
        }

        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        final Clan clan = player.getClan();
        if (clan == null) {
            return;
        }

        if (!player.hasAccess(ClanAccess.MODIFY_RANKS)) {
            return;
        }

        final ClanMember member1 = clan.getClanMember(memberName);
        if ((member1 == null) || (member1.getObjectId() == clan.getLeaderId())) {
            return;
        }

        final ClanMember member2 = clan.getClanMember(selectedMember);
        if ((member2 == null) || (member2.getObjectId() == clan.getLeaderId())) {
            return;
        }

        final int oldPledgeType = member1.getPledgeType();
        if (oldPledgeType == newPledgeType) {
            return;
        }

        member1.setPledgeType(newPledgeType);
        member2.setPledgeType(oldPledgeType);
        clan.broadcastClanStatus();
    }
}
