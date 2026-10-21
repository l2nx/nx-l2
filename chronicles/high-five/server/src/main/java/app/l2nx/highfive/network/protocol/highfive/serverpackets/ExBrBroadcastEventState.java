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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * Special event info packet.
 * @author Kerberos, mrTJO
 */
public class ExBrBroadcastEventState extends ServerPacket {
    public static final int APRIL_FOOLS = 20090401;
    public static final int EVAS_INFERNO = 20090801; // event state (0 - hide, 1 - show), day (1-14), percent (0-100)
    public static final int HALLOWEEN_EVENT = 20091031; // event state (0 - hide, 1 - show)
    public static final int RAISING_RUDOLPH = 20091225; // event state (0 - hide, 1 - show)
    public static final int LOVERS_JUBILEE = 20100214; // event state (0 - hide, 1 - show)

    private final int eventId;
    private final int eventState;
    private int param0;
    private int param1;
    private int param2;
    private int param3;
    private int param4;
    private String param5;
    private String param6;

    public ExBrBroadcastEventState(int eventId, int eventState) {
        this.eventId = eventId;
        this.eventState = eventState;
    }

    public ExBrBroadcastEventState(
            int eventId,
            int eventState,
            int param0,
            int param1,
            int param2,
            int param3,
            int param4,
            String param5,
            String param6) {
        this.eventId = eventId;
        this.eventState = eventState;
        this.param0 = param0;
        this.param1 = param1;
        this.param2 = param2;
        this.param3 = param3;
        this.param4 = param4;
        this.param5 = param5;
        this.param6 = param6;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_BR_BROADCAST_EVENT_STATE.writeId(this, buffer);
        buffer.writeInt(eventId);
        buffer.writeInt(eventState);
        buffer.writeInt(param0);
        buffer.writeInt(param1);
        buffer.writeInt(param2);
        buffer.writeInt(param3);
        buffer.writeInt(param4);
        buffer.writeString(param5);
        buffer.writeString(param6);
    }
}
