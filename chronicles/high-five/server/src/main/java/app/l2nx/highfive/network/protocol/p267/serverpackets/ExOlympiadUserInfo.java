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

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.olympiad.Participant;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author godson
 */
public class ExOlympiadUserInfo extends ServerPacket {
    private final Player player;
    private Participant par = null;
    private int curHp;
    private int maxHp;
    private int curCp;
    private int maxCp;

    public ExOlympiadUserInfo(Player player) {
        this.player = player;
        if (this.player != null) {
            curHp = (int) this.player.getCurrentHp();
            maxHp = this.player.getMaxHp();
            curCp = (int) this.player.getCurrentCp();
            maxCp = this.player.getMaxCp();
        } else {
            curHp = 0;
            maxHp = 100;
            curCp = 0;
            maxCp = 100;
        }
    }

    public ExOlympiadUserInfo(Participant par) {
        this.par = par;
        player = par.getPlayer();
        if (player != null) {
            curHp = (int) player.getCurrentHp();
            maxHp = player.getMaxHp();
            curCp = (int) player.getCurrentCp();
            maxCp = player.getMaxCp();
        } else {
            curHp = 0;
            maxHp = 100;
            curCp = 0;
            maxCp = 100;
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_OLYMPIAD_USER_INFO.writeId(this, buffer);
        if (player != null) {
            buffer.writeByte(player.getOlympiadSide());
            buffer.writeInt(player.getObjectId());
            buffer.writeString(player.getName());
            buffer.writeInt(player.getPlayerClass().getId());
        } else {
            buffer.writeByte(par.getSide());
            buffer.writeInt(par.getObjectId());
            buffer.writeString(par.getName());
            buffer.writeInt(par.getBaseClass());
        }

        buffer.writeInt(curHp);
        buffer.writeInt(maxHp);
        buffer.writeInt(curCp);
        buffer.writeInt(maxCp);
    }
}
