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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SiegeInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import java.util.Calendar;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;

/**
 * @author UnAfraid
 */
@Slf4j
public class RequestSetCastleSiegeTime extends ClientPacket {
    private int castleId;
    private long time;

    @Override
    protected void readImpl() {
        castleId = readInt();
        time = readInt();
        time *= 1000;
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        final Castle castle = CastleManager.getInstance().getCastleById(castleId);
        if ((player == null) || (castle == null)) {
            log.warn("[C]RequestSetCastleSiegeTime: activeChar: {} castle: {} castleId: {}", player, castle, castleId);
            return;
        }

        if ((castle.getOwnerId() > 0) && (castle.getOwnerId() != player.getClanId())) {
            log.warn(
                    "[C]RequestSetCastleSiegeTime: activeChar: {} castle: {} castleId: {} is trying to change siege date of not his own castle!",
                    player,
                    castle,
                    castleId);
        } else if (!player.isClanLeader()) {
            log.warn(
                    "[C]RequestSetCastleSiegeTime: activeChar: {} castle: {} castleId: {} is trying to change siege date but is not clan leader!",
                    player,
                    castle,
                    castleId);
        } else if (!castle.isTimeRegistrationOver()) {
            if (isSiegeTimeValid(castle.getSiegeDate().getTimeInMillis(), time)) {
                castle.setSiegeDate(time);
                castle.setTimeRegistrationOver(true);
                castle.getSiege().saveSiegeDate();
                final SystemMessage msg =
                        new SystemMessage(SystemMessageId.S1_HAS_ANNOUNCED_THE_NEXT_CASTLE_SIEGE_TIME);
                msg.addCastleId(castleId);
                World.broadcastToAllOnlinePlayers(msg);
                player.sendPacket(new SiegeInfo(castle, player));
            } else {
                log.warn(
                        "[C]RequestSetCastleSiegeTime: activeChar: {} castle: {} castleId: {} is trying to an invalid time ({} !",
                        player,
                        castle,
                        castleId,
                        new Date(time));
            }
        } else {
            log.warn(
                    "[C]RequestSetCastleSiegeTime: activeChar: {} castle: {} castleId: {} is trying to change siege date but currently not possible!",
                    player,
                    castle,
                    castleId);
        }
    }

    private static boolean isSiegeTimeValid(long siegeDate, long choosenDate) {
        final Calendar cal1 = ClockBridge.calendar();
        cal1.setTimeInMillis(siegeDate);
        cal1.set(Calendar.MINUTE, 0);
        cal1.set(Calendar.SECOND, 0);

        final Calendar cal2 = ClockBridge.calendar();
        cal2.setTimeInMillis(choosenDate);

        for (int hour : FeatureConfig.SIEGE_HOUR_LIST) {
            cal1.set(Calendar.HOUR_OF_DAY, hour);
            if (isEqual(
                    cal1,
                    cal2,
                    Calendar.YEAR,
                    Calendar.MONTH,
                    Calendar.DAY_OF_MONTH,
                    Calendar.HOUR,
                    Calendar.MINUTE,
                    Calendar.SECOND)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isEqual(Calendar cal1, Calendar cal2, int... fields) {
        for (int field : fields) {
            if (cal1.get(field) != cal2.get(field)) {
                return false;
            }
        }

        return true;
    }
}
