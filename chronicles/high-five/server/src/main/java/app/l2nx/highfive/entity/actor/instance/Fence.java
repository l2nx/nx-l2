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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.data.xml.FenceData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.FenceState;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.DeleteObject;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExColosseumFenceInfo;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * @author HorridoJoho
 */
@SingleWriter
public class Fence extends WorldObject {
    private final int xMin;

    private final int xMax;

    private final int yMin;

    private final int yMax;

    private final String name;

    private final int width;

    private final int length;

    private FenceState state;

    private int[] heightFences;

    public Fence(int x, int y, String name, int width, int length, int height, FenceState state) {
        super(IdManager.getInstance().getNextId());

        xMin = x - (width / 2);
        xMax = x + (width / 2);
        yMin = y - (length / 2);
        yMax = y + (length / 2);
        this.name = name;
        this.width = width;
        this.length = length;
        this.state = state;
        if (height > 1) {
            heightFences = new int[height - 1];
            for (int i = 0; i < heightFences.length; i++) {
                heightFences[i] = IdManager.getInstance().getNextId();
            }
        }
    }

    public int getXMin() {
        WorldPartition.checkOwner();
        return xMin;
    }

    public int getXMax() {
        WorldPartition.checkOwner();
        return xMax;
    }

    public int getYMin() {
        WorldPartition.checkOwner();
        return yMin;
    }

    public int getYMax() {
        WorldPartition.checkOwner();
        return yMax;
    }

    @Override
    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public int getWidth() {
        WorldPartition.checkOwner();
        return width;
    }

    public int getLength() {
        WorldPartition.checkOwner();
        return length;
    }

    public FenceState getState() {
        WorldPartition.checkOwner();
        return state;
    }

    @Override
    public int getId() {
        WorldPartition.checkOwner();
        return getObjectId();
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        return false;
    }

    @Override
    public void sendInfo(Player player) {
        WorldPartition.checkOwner();
        player.sendPacket(new ExColosseumFenceInfo(this));
        if (heightFences != null) {
            for (int objId : heightFences) {
                player.sendPacket(new ExColosseumFenceInfo(objId, getX(), getY(), getZ(), width, length, state));
            }
        }
    }

    @Override
    public boolean decayMe() {
        WorldPartition.checkOwner();
        if (heightFences != null) {
            final DeleteObject[] deleteObjects = new DeleteObject[heightFences.length];
            for (int i = 0; i < heightFences.length; i++) {
                deleteObjects[i] = new DeleteObject(heightFences[i]);
            }

            World.forEachVisibleObject(this, Player.class, player -> {
                for (int i = 0; i < heightFences.length; i++) {
                    player.sendPacket(deleteObjects[i]);
                }
            });
        }

        return super.decayMe();
    }

    public boolean deleteMe() {
        WorldPartition.checkOwner();
        decayMe();

        FenceData.getInstance().removeFence(this);
        return false;
    }

    public void setState(FenceState type) {
        WorldPartition.checkOwner();
        state = type;
        broadcastInfo();
    }

    @Override
    public boolean isFence() {
        WorldPartition.checkOwner();
        return true;
    }
}
