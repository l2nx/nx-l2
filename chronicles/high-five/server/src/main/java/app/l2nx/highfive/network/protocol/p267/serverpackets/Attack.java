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
import app.l2nx.highfive.entity.actor.holders.creature.Hit;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Attack extends ServerPacket {
    private final int attackerObjId;
    private final boolean soulshot;
    private final int ssGrade;
    private final Location attackerLoc;
    private final Location targetLoc;
    private final List<Hit> hits = new ArrayList<>();

    /**
     * @param attacker
     * @param target
     * @param useShots
     * @param ssGrade
     */
    public Attack(Creature attacker, Creature target, boolean useShots, int ssGrade) {
        attackerObjId = attacker.getObjectId();
        soulshot = useShots;
        this.ssGrade = ssGrade;
        attackerLoc = new Location(attacker);
        targetLoc = new Location(target);
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
        hits.add(new Hit(target, damage, miss, crit, shld, soulshot, ssGrade));
    }

    /**
     * @return {@code true} if current attack contains at least 1 hit.
     */
    public boolean hasHits() {
        return !hits.isEmpty();
    }

    /**
     * @return {@code true} if attack has soul shot charged.
     */
    public boolean hasSoulshot() {
        return soulshot;
    }

    /**
     * Writes current hit
     * @param hit
     * @param buffer
     */
    private void writeHit(Hit hit, PacketWriter buffer) {
        buffer.writeInt(hit.getTargetId());
        buffer.writeInt(hit.getDamage());
        buffer.writeByte(hit.getFlags());
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        final Iterator<Hit> it = hits.iterator();
        ServerPackets.ATTACK.writeId(this, buffer);
        buffer.writeInt(attackerObjId);
        writeHit(it.next(), buffer);
        buffer.writeInt(attackerLoc.getX());
        buffer.writeInt(attackerLoc.getY());
        buffer.writeInt(attackerLoc.getZ());
        buffer.writeShort(hits.size() - 1);
        while (it.hasNext()) {
            writeHit(it.next(), buffer);
        }

        buffer.writeInt(targetLoc.getX());
        buffer.writeInt(targetLoc.getY());
        buffer.writeInt(targetLoc.getZ());
    }
}
