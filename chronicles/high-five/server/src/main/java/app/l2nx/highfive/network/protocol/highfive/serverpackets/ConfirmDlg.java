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
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage.SMParam;
import app.l2nx.network.codec.PacketWriter;
import lombok.Getter;

/**
 * @author kombat
 */
public class ConfirmDlg extends ServerPacket {
    private int time;
    private int requesterId;

    @Getter
    private final SystemMessage systemMessage;

    public ConfirmDlg(SystemMessageId smId) {
        systemMessage = new SystemMessage(smId);
    }

    public ConfirmDlg(int id) {
        systemMessage = new SystemMessage(id);
    }

    public ConfirmDlg(String text) {
        systemMessage = new SystemMessage(SystemMessageId.S1_3);
        systemMessage.addString(text);
    }

    public ConfirmDlg addTime(int time) {
        this.time = time;
        return this;
    }

    public ConfirmDlg addRequesterId(int id) {
        requesterId = id;
        return this;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.CONFIRM_DLG.writeId(this, buffer);
        final SMParam[] params = systemMessage.getParams();
        buffer.writeInt(systemMessage.getId());
        buffer.writeInt(params.length);
        for (SMParam param : params) {
            buffer.writeInt(param.getType());
            switch (param.getType()) {
                case SystemMessage.TYPE_TEXT:
                case SystemMessage.TYPE_PLAYER_NAME: {
                    buffer.writeString(param.getStringValue());
                    break;
                }
                case SystemMessage.TYPE_LONG_NUMBER: {
                    buffer.writeLong(param.getLongValue());
                    break;
                }
                case SystemMessage.TYPE_ITEM_NAME:
                case SystemMessage.TYPE_CASTLE_NAME:
                case SystemMessage.TYPE_INT_NUMBER:
                case SystemMessage.TYPE_NPC_NAME:
                case SystemMessage.TYPE_ELEMENT_NAME:
                case SystemMessage.TYPE_SYSTEM_STRING:
                case SystemMessage.TYPE_INSTANCE_NAME:
                case SystemMessage.TYPE_DOOR_NAME: {
                    buffer.writeInt(param.getIntValue());
                    break;
                }
                case SystemMessage.TYPE_SKILL_NAME: {
                    final int[] array = param.getIntArrayValue();
                    buffer.writeInt(array[0]); // SkillId
                    buffer.writeInt(array[1]); // SkillLevel
                    break;
                }
                case SystemMessage.TYPE_ZONE_NAME: {
                    final int[] array = param.getIntArrayValue();
                    buffer.writeInt(array[0]); // x
                    buffer.writeInt(array[1]); // y
                    buffer.writeInt(array[2]); // z
                    break;
                }
            }
        }

        buffer.writeInt(time);
        buffer.writeInt(requesterId);
    }
}
