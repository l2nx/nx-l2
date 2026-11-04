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

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import app.l2nx.protocol.game.p267.serverpackets.MagicSkillUse.GroundLocation;
import java.util.List;

/**
 * MagicSkillUse server packet implementation.
 * @author Mobius
 */
public class MagicSkillUse extends ServerPacket {
    private final app.l2nx.protocol.game.p267.serverpackets.MagicSkillUse wire;

    public MagicSkillUse(Creature creature, Creature target, int skillId, int skillLevel, int hitTime, int reuseDelay) {
        wire = app.l2nx.protocol.game.p267.serverpackets.MagicSkillUse.builder()
                .objectId(creature.getObjectId())
                .targetObjectId(target.getObjectId())
                .skillId(skillId)
                .skillLevel(skillLevel)
                .hitTime(hitTime)
                .reuseDelay(reuseDelay)
                .x(creature.getX())
                .y(creature.getY())
                .z(creature.getZ())
                .groundLocations(groundLocations(creature))
                .targetX(target.getX())
                .targetY(target.getY())
                .targetZ(target.getZ())
                .build();
    }

    public MagicSkillUse(Creature creature, int skillId, int skillLevel, int hitTime, int reuseDelay) {
        this(creature, creature, skillId, skillLevel, hitTime, reuseDelay);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.MAGIC_SKILL_USE.debug(this);
        wire.write(buffer.buffer());
    }

    private static List<GroundLocation> groundLocations(Creature creature) {
        final Location groundLocation =
                creature.isPlayer() ? creature.asPlayer().getCurrentSkillWorldPosition() : null;
        if (groundLocation == null) {
            return List.of();
        }
        return List.of(GroundLocation.builder()
                .x(groundLocation.getX())
                .y(groundLocation.getY())
                .z(groundLocation.getZ())
                .build());
    }
}
