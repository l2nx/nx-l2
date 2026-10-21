/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
package app.l2nx.highfive.scripts.handlers.chat.commands.user;

import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.handler.IUserCommandHandler;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.clan.model.WarOpponent;
import java.util.List;

/**
 * Clan War Start, Under Attack List, War List user commands.
 * @author Tempy
 */
public class ClanWarsList implements IUserCommandHandler {
    private static final int[] COMMAND_IDS = {88, 89, 90};

    @Override
    public boolean onCommand(int id, Player player) {
        if ((id != COMMAND_IDS[0]) && (id != COMMAND_IDS[1]) && (id != COMMAND_IDS[2])) {
            return false;
        }

        final Clan clan = player.getClan();
        if (clan == null) {
            player.sendPacket(SystemMessageId.NOT_JOINED_IN_ANY_CLAN);
            return false;
        }

        final List<WarOpponent> opponents;

        // Attack List
        if (id == 88) {
            player.sendPacket(SystemMessageId.CLANS_YOU_VE_DECLARED_WAR_ON);
            opponents = ClanTable.getInstance().findDeclaredWarsOnly(clan.getId());
        }
        // Under Attack List
        else if (id == 89) {
            player.sendPacket(SystemMessageId.CLANS_THAT_HAVE_DECLARED_WAR_ON_YOU);
            opponents = ClanTable.getInstance().findAttackersOnly(clan.getId());
        }
        // War List
        else {
            player.sendPacket(SystemMessageId.WAR_LIST);
            opponents = ClanTable.getInstance().findMutualWars(clan.getId());
        }

        for (WarOpponent opponent : opponents) {
            final SystemMessage sm;
            if (opponent.allyId() > 0) {
                // Target With Ally
                sm = new SystemMessage(SystemMessageId.S1_S2_ALLIANCE);
                sm.addString(opponent.name());
                sm.addString(opponent.allyName());
            } else {
                // Target Without Ally
                sm = new SystemMessage(SystemMessageId.S1_NO_ALLIANCE_EXISTS);
                sm.addString(opponent.name());
            }

            player.sendPacket(sm);
        }

        player.sendPacket(SystemMessageId.EMPTY_3);
        return true;
    }

    @Override
    public int[] getCommandList() {
        return COMMAND_IDS;
    }
}
