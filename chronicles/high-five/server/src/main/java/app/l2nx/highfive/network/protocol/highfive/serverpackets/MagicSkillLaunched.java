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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Collection;
import java.util.Collections;

public class MagicSkillLaunched extends ServerPacket {
    private final int objectId;
    private final int skillId;
    private final int skillLevel;
    private final Collection<WorldObject> targets;

    public MagicSkillLaunched(Creature creature, int skillId, int skillLevel, Collection<WorldObject> targets) {
        objectId = creature.getObjectId();
        this.skillId = skillId;
        this.skillLevel = skillLevel;
        if (targets == null) {
            this.targets = Collections.singletonList(creature);
            return;
        }

        this.targets = targets;
    }

    public MagicSkillLaunched(Creature creature, int skillId, int skillLevel, WorldObject target) {
        this(creature, skillId, skillLevel, Collections.singletonList(target == null ? creature : target));
    }

    public MagicSkillLaunched(Creature creature, int skillId, int skillLevel) {
        this(creature, skillId, skillLevel, Collections.singletonList(creature));
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.MAGIC_SKILL_LAUNCHED.writeId(this, buffer);
        buffer.writeInt(objectId);
        buffer.writeInt(skillId);
        buffer.writeInt(skillLevel);
        buffer.writeInt(targets.size());
        for (WorldObject target : targets) {
            buffer.writeInt(target.getObjectId());
        }
    }
}
