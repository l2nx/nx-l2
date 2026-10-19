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
package app.l2nx.highfive.network.serverpackets;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

/**
 * MagicSkillUse server packet implementation.
 * @author Mobius
 */
public class MagicSkillUse extends ServerPacket {
    private final int skillId;
    private final int skillLevel;
    private final int hitTime;
    private final int reuseDelay;
    private final Creature creature;
    private final Creature target;
    private final Location groundLocation;

    public MagicSkillUse(Creature creature, Creature target, int skillId, int skillLevel, int hitTime, int reuseDelay) {
        this.creature = creature;
        this.target = target;
        this.skillId = skillId;
        this.skillLevel = skillLevel;
        this.hitTime = hitTime;
        this.reuseDelay = reuseDelay;
        groundLocation = creature.isPlayer() ? creature.asPlayer().getCurrentSkillWorldPosition() : null;
    }

    public MagicSkillUse(Creature creature, int skillId, int skillLevel, int hitTime, int reuseDelay) {
        this(creature, creature, skillId, skillLevel, hitTime, reuseDelay);
    }

    @Override
    public void writeImpl(GameClient client, WriteBuffer buffer) {
        ServerPackets.MAGIC_SKILL_USE.writeId(this, buffer);
        buffer.writeInt(creature.getObjectId());
        buffer.writeInt(target.getObjectId());
        buffer.writeInt(skillId);
        buffer.writeInt(skillLevel);
        buffer.writeInt(hitTime);
        buffer.writeInt(reuseDelay);
        buffer.writeInt(creature.getX());
        buffer.writeInt(creature.getY());
        buffer.writeInt(creature.getZ());
        buffer.writeShort(0); // isGroundTargetSkill ? 65535 : 0
        if (groundLocation == null) {
            buffer.writeShort(0);
        } else {
            buffer.writeShort(1);
            buffer.writeInt(groundLocation.getX());
            buffer.writeInt(groundLocation.getY());
            buffer.writeInt(groundLocation.getZ());
        }

        buffer.writeInt(target.getX());
        buffer.writeInt(target.getY());
        buffer.writeInt(target.getZ());
    }
}
