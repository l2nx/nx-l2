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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.network.SystemMessageId;

public class AllyDismiss extends ClientPacket {
    private String clanName;

    @Override
    protected void readImpl() {
        clanName = readString();
    }

    @Override
    protected void runImpl() {
        if (clanName == null) {
            return;
        }

        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (player.getClan() == null) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_A_CLAN_MEMBER_AND_CANNOT_PERFORM_THIS_ACTION);
            return;
        }

        final Clan leaderClan = player.getClan();
        if (leaderClan.getAllyId() == 0) {
            player.sendPacket(SystemMessageId.YOU_ARE_NOT_CURRENTLY_ALLIED_WITH_ANY_CLANS);
            return;
        }

        if (!player.isClanLeader() || (leaderClan.getId() != leaderClan.getAllyId())) {
            player.sendPacket(SystemMessageId.THIS_FEATURE_IS_ONLY_AVAILABLE_TO_ALLIANCE_LEADERS);
            return;
        }

        final Clan clan = ClanTable.getInstance().getClanByName(clanName);
        if (clan == null) {
            player.sendPacket(SystemMessageId.THAT_CLAN_DOES_NOT_EXIST);
            return;
        }

        if (clan.getId() == leaderClan.getId()) {
            player.sendPacket(SystemMessageId.ALLIANCE_LEADERS_CANNOT_WITHDRAW);
            return;
        }

        if (clan.getAllyId() != leaderClan.getAllyId()) {
            player.sendPacket(SystemMessageId.DIFFERENT_ALLIANCE);
            return;
        }

        final long currentTime = ClockBridge.wallMillis();
        leaderClan.setAllyPenaltyExpiryTime(
                currentTime + (PlayerConfig.ALT_ACCEPT_CLAN_DAYS_WHEN_DISMISSED * 86400000),
                Clan.PENALTY_TYPE_DISMISS_CLAN); // 24*60*60*1000 = 86400000
        leaderClan.updateClanInDB();

        clan.setAllyId(0);
        clan.setAllyName(null);
        clan.changeAllyCrest(0, true);
        clan.setAllyPenaltyExpiryTime(
                currentTime + (PlayerConfig.ALT_ALLY_JOIN_DAYS_WHEN_DISMISSED * 86400000),
                Clan.PENALTY_TYPE_CLAN_DISMISSED); // 24*60*60*1000 = 86400000
        clan.updateClanInDB();

        player.sendPacket(SystemMessageId.YOU_HAVE_SUCCEEDED_IN_EXPELLING_THE_CLAN);
    }
}
