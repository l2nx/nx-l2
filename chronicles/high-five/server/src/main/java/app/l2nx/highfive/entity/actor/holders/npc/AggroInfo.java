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
package app.l2nx.highfive.entity.actor.holders.npc;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * Hate (target selection) and damage (contribution) of one aggressor, both capped at {@link #LIMIT}. Belongs to the
 * world loop like the aggro map of {@code Attackable}.
 * @author BazookaRpm
 */
@SingleWriter
public class AggroInfo {
    private static final long LIMIT = 1000000000000000L;

    private final Creature attacker;

    private long damage;

    private long hate;

    public AggroInfo(Creature aggressor) {
        attacker = aggressor;
        damage = 0;
        hate = 0;
    }

    public Creature getAttacker() {
        WorldPartition.checkOwner();
        return attacker;
    }

    public long getDamage() {
        WorldPartition.checkOwner();
        return damage;
    }

    public long getHate() {
        WorldPartition.checkOwner();
        return hate;
    }

    public void addDamage(long value) {
        WorldPartition.checkOwner();
        if (value == 0) {
            return;
        }

        if (value > 0) {
            final long current = damage;
            damage = (current >= (LIMIT - value)) ? LIMIT : (current + value);
            return;
        }

        damage += value;
    }

    public void addHate(long value) {
        WorldPartition.checkOwner();
        if (value == 0) {
            return;
        }

        if (value > 0) {
            final long current = hate;
            hate = (current >= (LIMIT - value)) ? LIMIT : (current + value);
            return;
        }

        hate += value;
    }

    public void stopHate() {
        WorldPartition.checkOwner();
        hate = 0;
    }

    public long checkHate(Creature owner) {
        WorldPartition.checkOwner();
        final Creature attacker = this.attacker;
        if ((attacker == null) || (owner == null)) {
            hate = 0;
            return 0;
        }

        if (attacker.isAlikeDead()) {
            hate = 0;
            return 0;
        }

        if (!attacker.isSpawned()) {
            hate = 0;
            return 0;
        }

        if (!owner.isInSurroundingRegion(attacker)) {
            hate = 0;
        }

        return hate;
    }

    @Override
    public int hashCode() {
        // WorldObject hashes by its object id without the ownership check, which equals and hashCode must not take.
        final Creature c = attacker;
        return (c != null) ? c.hashCode() : 0;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof AggroInfo)) {
            return false;
        }

        return ((AggroInfo) other).attacker == attacker;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(96);
        sb.append("AggroInfo [attacker=");
        sb.append(attacker);
        sb.append(", hate=");
        sb.append(hate);
        sb.append(", damage=");
        sb.append(damage);
        sb.append(']');
        return sb.toString();
    }
}
