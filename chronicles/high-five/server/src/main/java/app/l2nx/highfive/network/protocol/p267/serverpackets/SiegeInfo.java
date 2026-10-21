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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.residences.ClanHall;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Calendar;
import lombok.extern.slf4j.Slf4j;

/**
 * Shows the Siege Info<br>
 * <br>
 * c = c9<br>
 * d = CastleID<br>
 * d = Show Owner Controls (0x00 default || >=0x02(mask?) owner)<br>
 * d = Owner ClanID<br>
 * S = Owner ClanName<br>
 * S = Owner Clan LeaderName<br>
 * d = Owner AllyID<br>
 * S = Owner AllyName<br>
 * d = current time (seconds)<br>
 * d = Siege time (seconds) (0 for selectable)<br>
 * d = (UNKNOW) Siege Time Select Related?
 * @author KenM
 */
@Slf4j
public class SiegeInfo extends ServerPacket {
    private Castle castle;
    private ClanHall hall;
    private final Player player;

    public SiegeInfo(Castle castle, Player player) {
        this.castle = castle;
        this.player = player;
    }

    public SiegeInfo(ClanHall hall, Player player) {
        this.hall = hall;
        this.player = player;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.CASTLE_SIEGE_INFO.writeId(this, buffer);
        if (castle != null) {
            buffer.writeInt(castle.getResidenceId());
            final int ownerId = castle.getOwnerId();
            buffer.writeInt((ownerId == player.getClanId()) && (player.isClanLeader()));
            buffer.writeInt(ownerId);
            if (ownerId > 0) {
                final Clan owner = ClanTable.getInstance().getClan(ownerId);
                if (owner != null) {
                    buffer.writeString(owner.getName()); // Clan Name
                    buffer.writeString(owner.getLeaderName()); // Clan Leader Name
                    buffer.writeInt(owner.getAllyId()); // Ally ID
                    buffer.writeString(owner.getAllyName()); // Ally Name
                } else {
                    log.warn("Null owner for castle: {}", castle.getName());
                }
            } else {
                buffer.writeString(""); // Clan Name
                buffer.writeString(""); // Clan Leader Name
                buffer.writeInt(0); // Ally ID
                buffer.writeString(""); // Ally Name
            }

            buffer.writeInt((int) (ClockBridge.wallMillis() / 1000));
            if (!castle.isTimeRegistrationOver()
                    && player.isClanLeader()
                    && (player.getClanId() == castle.getOwnerId())) {
                final Calendar cal = ClockBridge.calendar();
                cal.setTimeInMillis(castle.getSiegeDate().getTimeInMillis());
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                buffer.writeInt(0);
                buffer.writeInt(FeatureConfig.SIEGE_HOUR_LIST.size());
                for (int hour : FeatureConfig.SIEGE_HOUR_LIST) {
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    buffer.writeInt((int) (cal.getTimeInMillis() / 1000));
                }
            } else {
                buffer.writeInt((int) (castle.getSiegeDate().getTimeInMillis() / 1000));
                buffer.writeInt(0);
            }
        } else {
            buffer.writeInt(hall.getId());
            final int ownerId = hall.getOwnerId();
            buffer.writeInt(((ownerId == player.getClanId()) && (player.isClanLeader())));
            buffer.writeInt(ownerId);
            if (ownerId > 0) {
                final Clan owner = ClanTable.getInstance().getClan(ownerId);
                if (owner != null) {
                    buffer.writeString(owner.getName()); // Clan Name
                    buffer.writeString(owner.getLeaderName()); // Clan Leader Name
                    buffer.writeInt(owner.getAllyId()); // Ally ID
                    buffer.writeString(owner.getAllyName()); // Ally Name
                } else {
                    log.warn("Null owner for siegable hall: {}", hall.getName());
                }
            } else {
                buffer.writeString(""); // Clan Name
                buffer.writeString(""); // Clan Leader Name
                buffer.writeInt(0); // Ally ID
                buffer.writeString(""); // Ally Name
            }

            buffer.writeInt((int) (ClockBridge.wallMillis() / 1000));
            buffer.writeInt((int)
                    ((CHSiegeManager.getInstance().getSiegableHall(hall.getId()).getNextSiegeTime()) / 1000));
            buffer.writeInt(0); // number of choices?
        }
    }
}
