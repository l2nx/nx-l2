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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanAccess;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SiegeInfo;

/**
 * @author KenM
 */
public class RequestJoinSiege extends ClientPacket {
    private int castleId;
    private int isAttacker;
    private int isJoining;

    @Override
    protected void readImpl() {
        castleId = readInt();
        isAttacker = readInt();
        isJoining = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (!player.hasAccess(ClanAccess.CASTLE_SIEGE)) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
            return;
        }

        final Clan clan = player.getClan();
        if (clan == null) {
            return;
        }

        final Castle castle = CastleManager.getInstance().getCastleById(castleId);
        if (castle != null) {
            if (isJoining == 1) {
                if (ClockBridge.wallMillis() < clan.getDissolvingExpiryTime()) {
                    player.sendPacket(
                            SystemMessageId
                                    .YOUR_CLAN_MAY_NOT_REGISTER_TO_PARTICIPATE_IN_A_SIEGE_WHILE_UNDER_A_GRACE_PERIOD_OF_THE_CLAN_S_DISSOLUTION);
                    return;
                }

                if (isAttacker == 1) {
                    castle.getSiege().registerAttacker(player);
                } else {
                    castle.getSiege().registerDefender(player);
                }
            } else {
                castle.getSiege().removeSiegeClan(player);
            }

            castle.getSiege().listRegisterClan(player);
        }

        final SiegableHall hall = CHSiegeManager.getInstance().getSiegableHall(castleId);
        if (hall != null) {
            if (isJoining == 1) {
                if (ClockBridge.wallMillis() < clan.getDissolvingExpiryTime()) {
                    player.sendPacket(
                            SystemMessageId
                                    .YOUR_CLAN_MAY_NOT_REGISTER_TO_PARTICIPATE_IN_A_SIEGE_WHILE_UNDER_A_GRACE_PERIOD_OF_THE_CLAN_S_DISSOLUTION);
                    return;
                }

                CHSiegeManager.getInstance().registerClan(clan, hall, player);
            } else {
                CHSiegeManager.getInstance().unRegisterClan(clan, hall);
            }

            player.sendPacket(new SiegeInfo(hall, player));
        }
    }
}
