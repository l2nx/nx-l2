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

import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.StaticObject;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * @author KenM
 */
public class StaticObjectInfo extends ServerPacket {
    private final int staticObjectId;
    private final int objectId;
    private final int type;
    private final boolean targetable;
    private final int meshIndex;
    private final boolean closed;
    private final boolean enemy;
    private final int maxHp;
    private final int currentHp;
    private final boolean showHp;
    private final int damageGrade;

    public StaticObjectInfo(StaticObject staticObject) {
        staticObjectId = staticObject.getId();
        objectId = staticObject.getObjectId();
        type = 0;
        targetable = true;
        meshIndex = staticObject.getMeshIndex();
        closed = false;
        enemy = false;
        maxHp = 0;
        currentHp = 0;
        showHp = false;
        damageGrade = 0;
    }

    public StaticObjectInfo(Door door, boolean targetable) {
        staticObjectId = door.getId();
        objectId = door.getObjectId();
        type = 1;
        this.targetable = door.isTargetable() || targetable;
        meshIndex = door.getMeshIndex();
        closed = !door.isOpen();
        enemy = door.isEnemy();
        maxHp = door.getMaxHp();
        currentHp = (int) door.getCurrentHp();
        showHp = door.isShowHp();
        damageGrade = door.getDamage();
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.STATIC_OBJECT.writeId(this, buffer);
        buffer.writeInt(staticObjectId);
        buffer.writeInt(objectId);
        buffer.writeInt(type);
        buffer.writeInt(targetable);
        buffer.writeInt(meshIndex);
        buffer.writeInt(closed);
        buffer.writeInt(enemy);
        buffer.writeInt(currentHp);
        buffer.writeInt(maxHp);
        buffer.writeInt(showHp);
        buffer.writeInt(damageGrade);
    }
}
