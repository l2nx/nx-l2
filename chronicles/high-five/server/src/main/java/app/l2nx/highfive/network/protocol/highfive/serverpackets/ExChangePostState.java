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
 * @author Migi
 */
public class ExChangePostState extends ServerPacket {
    private final boolean receivedBoard;
    private final int[] changedMsgIds;
    private final int changeId;

    public ExChangePostState(boolean receivedBoard, int[] changedMsgIds, int changeId) {
        this.receivedBoard = receivedBoard;
        this.changedMsgIds = changedMsgIds;
        this.changeId = changeId;
    }

    public ExChangePostState(boolean receivedBoard, int changedMsgId, int changeId) {
        this.receivedBoard = receivedBoard;
        changedMsgIds = new int[] {changedMsgId};
        this.changeId = changeId;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_CHANGE_POST_STATE.writeId(this, buffer);
        buffer.writeInt(receivedBoard);
        buffer.writeInt(changedMsgIds.length);
        for (int postId : changedMsgIds) {
            buffer.writeInt(postId); // postId
            buffer.writeInt(changeId); // state
        }
    }
}
