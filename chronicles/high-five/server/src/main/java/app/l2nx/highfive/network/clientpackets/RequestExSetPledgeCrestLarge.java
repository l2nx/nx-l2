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

import app.l2nx.highfive.data.sql.CrestTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanAccess;
import app.l2nx.highfive.entity.clan.Crest;
import app.l2nx.highfive.entity.clan.enums.CrestType;
import app.l2nx.highfive.network.SystemMessageId;

/**
 * Format : chdb c (id) 0xD0 h (subid) 0x11 d data size b raw data (picture i think ;) )
 * @author -Wooden-
 */
public class RequestExSetPledgeCrestLarge extends ClientPacket {
    private int length;
    private byte[] data = null;

    @Override
    protected void readImpl() {
        length = readInt();
        if (length > 2176) {
            return;
        }

        data = readBytes(length);
    }

    @Override
    protected void runImpl() {
        if (data == null) {
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

        if ((length < 0) || (length > 2176)) {
            player.sendPacket(
                    SystemMessageId.THE_SIZE_OF_THE_UPLOADED_CREST_OR_INSIGNIA_DOES_NOT_MEET_THE_STANDARD_REQUIREMENTS);
            return;
        }

        if (clan.getDissolvingExpiryTime() > System.currentTimeMillis()) {
            player.sendPacket(
                    SystemMessageId
                            .AS_YOU_ARE_CURRENTLY_SCHEDULE_FOR_CLAN_DISSOLUTION_YOU_CANNOT_REGISTER_OR_DELETE_A_CLAN_CREST);
            return;
        }

        if (!player.hasAccess(ClanAccess.CHANGE_CREST)) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return;
        }

        if (length == 0) {
            if (clan.getCrestLargeId() != 0) {
                clan.changeLargeCrest(0);
                player.sendPacket(SystemMessageId.THE_CLAN_S_CREST_HAS_BEEN_DELETED);
            }
        } else {
            if (clan.getLevel() < 3) {
                player.sendPacket(
                        SystemMessageId.A_CLAN_CREST_CAN_ONLY_BE_REGISTERED_WHEN_THE_CLAN_S_SKILL_LEVEL_IS_3_OR_ABOVE);
                return;
            }

            final Crest crest = CrestTable.getInstance().createCrest(data, CrestType.PLEDGE_LARGE);
            if (crest != null) {
                clan.changeLargeCrest(crest.getId());
                player.sendPacket(
                        SystemMessageId
                                .THE_CLAN_CREST_WAS_SUCCESSFULLY_REGISTERED_REMEMBER_ONLY_A_CLAN_THAT_OWNS_A_CLAN_HALL_OR_CASTLE_CAN_DISPLAY_A_CREST);
            }
        }
    }
}
