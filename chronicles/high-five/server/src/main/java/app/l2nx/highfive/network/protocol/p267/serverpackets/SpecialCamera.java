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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class SpecialCamera extends ServerPacket {
    private final int id;
    private final int force;
    private final int angle1;
    private final int angle2;
    private final int time;
    private final int duration;
    private final int relYaw;
    private final int relPitch;
    private final int isWide;
    private final int relAngle;
    private final int unk;

    /**
     * Special Camera packet constructor.
     * @param creature the creature
     * @param force
     * @param angle1
     * @param angle2
     * @param time
     * @param range
     * @param duration
     * @param relYaw
     * @param relPitch
     * @param isWide
     * @param relAngle
     */
    public SpecialCamera(
            Creature creature,
            int force,
            int angle1,
            int angle2,
            int time,
            int range,
            int duration,
            int relYaw,
            int relPitch,
            int isWide,
            int relAngle) {
        this(creature, force, angle1, angle2, time, duration, range, relYaw, relPitch, isWide, relAngle, 0);
    }

    /**
     * Special Camera Ex packet constructor.
     * @param creature the creature
     * @param talker
     * @param force
     * @param angle1
     * @param angle2
     * @param time
     * @param duration
     * @param relYaw
     * @param relPitch
     * @param isWide
     * @param relAngle
     */
    public SpecialCamera(
            Creature creature,
            Creature talker,
            int force,
            int angle1,
            int angle2,
            int time,
            int duration,
            int relYaw,
            int relPitch,
            int isWide,
            int relAngle) {
        this(creature, force, angle1, angle2, time, duration, 0, relYaw, relPitch, isWide, relAngle, 0);
    }

    /**
     * Special Camera 3 packet constructor.
     * @param creature the creature
     * @param force
     * @param angle1
     * @param angle2
     * @param time
     * @param range
     * @param duration
     * @param relYaw
     * @param relPitch
     * @param isWide
     * @param relAngle
     * @param unk unknown post-C4 parameter
     */
    public SpecialCamera(
            Creature creature,
            int force,
            int angle1,
            int angle2,
            int time,
            int range,
            int duration,
            int relYaw,
            int relPitch,
            int isWide,
            int relAngle,
            int unk) {
        id = creature.getObjectId();
        this.force = force;
        this.angle1 = angle1;
        this.angle2 = angle2;
        this.time = time;
        this.duration = duration;
        this.relYaw = relYaw;
        this.relPitch = relPitch;
        this.isWide = isWide;
        this.relAngle = relAngle;
        this.unk = unk;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.SPECIAL_CAMERA.writeId(this, buffer);
        buffer.writeInt(id);
        buffer.writeInt(force);
        buffer.writeInt(angle1);
        buffer.writeInt(angle2);
        buffer.writeInt(time);
        buffer.writeInt(duration);
        buffer.writeInt(relYaw);
        buffer.writeInt(relPitch);
        buffer.writeInt(isWide);
        buffer.writeInt(relAngle);
        buffer.writeInt(unk);
    }
}
