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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanAccess;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ExShowDominionRegistry;

/**
 * @author Gigiikun
 */
public class RequestJoinDominionWar extends ClientPacket {
    private int territoryId;
    private int isClan;
    private int isJoining;

    @Override
    protected void readImpl() {
        territoryId = readInt();
        isClan = readInt();
        isJoining = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (TerritoryWarManager.getInstance().isRegistrationOver()) {
            player.sendPacket(
                    SystemMessageId
                            .IT_IS_NOT_A_TERRITORY_WAR_REGISTRATION_PERIOD_SO_A_REQUEST_CANNOT_BE_MADE_AT_THIS_TIME);
            return;
        }

        final Clan clan = player.getClan();
        final int castleId = territoryId - 80;
        if ((clan != null)
                && (TerritoryWarManager.getInstance().getTerritory(castleId).getOwnerClan() == clan)) {
            player.sendPacket(
                    SystemMessageId
                            .THE_CLAN_WHO_OWNS_THE_TERRITORY_CANNOT_PARTICIPATE_IN_THE_TERRITORY_WAR_AS_MERCENARIES);
            return;
        }

        if (isClan == 0x01) {
            if (!player.hasAccess(ClanAccess.CASTLE_SIEGE)) {
                player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
                return;
            }

            if (clan == null) {
                return;
            }

            if (isJoining == 1) {
                if (ClockBridge.wallMillis() < clan.getDissolvingExpiryTime()) {
                    player.sendPacket(
                            SystemMessageId
                                    .YOUR_CLAN_MAY_NOT_REGISTER_TO_PARTICIPATE_IN_A_SIEGE_WHILE_UNDER_A_GRACE_PERIOD_OF_THE_CLAN_S_DISSOLUTION);
                    return;
                } else if (TerritoryWarManager.getInstance().checkIsRegistered(-1, clan)) {
                    player.sendPacket(
                            SystemMessageId.YOU_VE_ALREADY_REQUESTED_A_TERRITORY_WAR_IN_ANOTHER_TERRITORY_ELSEWHERE);
                    return;
                }

                TerritoryWarManager.getInstance().registerClan(castleId, clan);
            } else {
                TerritoryWarManager.getInstance().removeClan(castleId, clan);
            }
        } else {
            if ((player.getLevel() < 40) || (player.getPlayerClass().level() < 2)) {
                // TODO: punish player
                return;
            }

            if (isJoining == 1) {
                if (TerritoryWarManager.getInstance().checkIsRegistered(-1, player.getObjectId())) {
                    player.sendPacket(
                            SystemMessageId.YOU_VE_ALREADY_REQUESTED_A_TERRITORY_WAR_IN_ANOTHER_TERRITORY_ELSEWHERE);
                    return;
                } else if ((clan != null) && TerritoryWarManager.getInstance().checkIsRegistered(-1, clan)) {
                    player.sendPacket(
                            SystemMessageId.YOU_VE_ALREADY_REQUESTED_A_TERRITORY_WAR_IN_ANOTHER_TERRITORY_ELSEWHERE);
                    return;
                }

                TerritoryWarManager.getInstance().registerMerc(castleId, player);
            } else {
                TerritoryWarManager.getInstance().removeMerc(castleId, player);
            }
        }

        player.sendPacket(new ExShowDominionRegistry(castleId, player));
    }
}
