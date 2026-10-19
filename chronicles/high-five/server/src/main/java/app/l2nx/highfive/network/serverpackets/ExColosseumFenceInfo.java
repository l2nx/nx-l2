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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.actor.enums.creature.FenceState;
import app.l2nx.highfive.entity.actor.instance.Fence;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author HorridoJoho
 */
public class ExColosseumFenceInfo extends ServerPacket {
    private final int objId;
    private final int x;
    private final int y;
    private final int z;
    private final int width;
    private final int length;
    private final int clientState;

    public ExColosseumFenceInfo(Fence fence) {
        this(
                fence.getObjectId(),
                fence.getX(),
                fence.getY(),
                fence.getZ(),
                fence.getWidth(),
                fence.getLength(),
                fence.getState());
    }

    public ExColosseumFenceInfo(int objId, double x, double y, double z, int width, int length, FenceState state) {
        this.objId = objId;
        this.x = (int) x;
        this.y = (int) y;
        this.z = (int) z;
        this.width = width;
        this.length = length;
        clientState = state.getClientId();
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.EX_COLOSSEUM_FENCE_INFO.writeId(this, buffer);
        buffer.writeInt(objId);
        buffer.writeInt(clientState);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(width);
        buffer.writeInt(length);
    }
}
