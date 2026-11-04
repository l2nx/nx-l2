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
import app.l2nx.protocol.game.p267.serverpackets.Attack.Hit;
import java.util.ArrayList;
import java.util.List;

public class Attack extends ServerPacket {
    private final boolean soulshot;
    private final int ssGrade;
    private final app.l2nx.protocol.game.p267.serverpackets.Attack.AttackBuilder wire;
    private final List<Hit> extraHits = new ArrayList<>();
    private Hit firstHit;
    private app.l2nx.protocol.game.p267.serverpackets.Attack packet;

    /**
     * @param attacker
     * @param target
     * @param useShots
     * @param ssGrade
     */
    public Attack(Creature attacker, Creature target, boolean useShots, int ssGrade) {
        soulshot = useShots;
        this.ssGrade = ssGrade;
        wire = app.l2nx.protocol.game.p267.serverpackets.Attack.builder()
                .attackerObjectId(attacker.getObjectId())
                .x(attacker.getX())
                .y(attacker.getY())
                .z(attacker.getZ())
                .targetX(target.getX())
                .targetY(target.getY())
                .targetZ(target.getZ());
    }

    /**
     * Adds hit to the attack (Attacks such as dual dagger/sword/fist has two hits)
     * @param target
     * @param damage
     * @param miss
     * @param crit
     * @param shld
     */
    public void addHit(Creature target, int damage, boolean miss, boolean crit, byte shld) {
        final app.l2nx.highfive.entity.actor.holders.creature.Hit hit =
                new app.l2nx.highfive.entity.actor.holders.creature.Hit(
                        target, damage, miss, crit, shld, soulshot, ssGrade);
        final Hit wireHit = Hit.builder()
                .targetObjectId(hit.getTargetId())
                .damage(hit.getDamage())
                .flags(hit.getFlags())
                .build();
        if (firstHit == null) {
            firstHit = wireHit;
        } else {
            extraHits.add(wireHit);
        }
    }

    /**
     * @return {@code true} if current attack contains at least 1 hit.
     */
    public boolean hasHits() {
        return firstHit != null;
    }

    /**
     * @return {@code true} if attack has soul shot charged.
     */
    public boolean hasSoulshot() {
        return soulshot;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.ATTACK.debug(this);
        // A broadcast writes it once per receiver, and every hit is in by the first write.
        if (packet == null) {
            packet = wire.hit(firstHit).extraHits(extraHits).build();
        }
        packet.write(buffer.buffer());
    }
}
