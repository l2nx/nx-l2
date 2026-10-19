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

import app.l2nx.highfive.entity.actor.Creature;
import lombok.Getter;

/**
 * Holds aggro-related information for a single aggressor against an NPC.
 * <p>
 * This holder tracks two independent counters:
 * </p>
 * <ul>
 * <li><b>Hate</b>: used for target selection / threat calculations.</li>
 * <li><b>Damage</b>: used for contribution tracking and auxiliary logic.</li>
 * </ul>
 * <p>
 * Values are capped to avoid uncontrolled growth. This class is intentionally lightweight and mutable. Concurrency control, if needed, should be handled by the owner container (e.g. the aggro map in {@code Attackable}).
 * </p>
 * @author BazookaRpm
 */
public class AggroInfo {
    /**
     * Upper cap applied to accumulated hate/damage.
     */
    private static final long LIMIT = 1000000000000000L;

    /**
     * The aggressor (attacker) this aggro entry belongs to.
     */
    @Getter
    private final Creature attacker;

    /**
     * Total accumulated damage credited to the aggressor.
     */
    @Getter
    private long damage;

    /**
     * Total accumulated hate credited to the aggressor.
     */
    @Getter
    private long hate;

    /**
     * Creates a new aggro entry bound to a specific aggressor.
     * @param aggressor the attacker for this entry
     */
    public AggroInfo(Creature aggressor) {
        attacker = aggressor;
        damage = 0;
        hate = 0;
    }

    /**
     * Adds damage to this entry.
     * <p>
     * Positive values are capped to {@link #LIMIT}. Negative values are applied as-is.
     * </p>
     * @param value the damage delta to add
     */
    public void addDamage(long value) {
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

    /**
     * Adds hate to this entry.
     * <p>
     * Positive values are capped to {@link #LIMIT}. Negative values are applied as-is.
     * </p>
     * @param value the hate delta to add
     */
    public void addHate(long value) {
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

    /**
     * Resets hate to zero.
     */
    public void stopHate() {
        hate = 0;
    }

    /**
     * Validates whether the stored hate should remain active for the current owner context.
     * <p>
     * Hate is reset when:
     * </p>
     * <ul>
     * <li>Owner or aggressor is null.</li>
     * <li>Aggressor is dead or not spawned.</li>
     * <li>Aggressor is outside the owner's surrounding region.</li>
     * </ul>
     * @param owner the NPC/creature owning this aggro list
     * @return the current hate value after validation
     */
    public long checkHate(Creature owner) {
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

    /**
     * Hash is based on the aggressor object id.
     * @return the aggressor object id, or {@code 0} if aggressor is null
     */
    @Override
    public int hashCode() {
        final Creature c = attacker;
        return (c != null) ? c.getObjectId() : 0;
    }

    /**
     * Equality is based on aggressor identity (same instance).
     * @param other the object to compare with
     * @return {@code true} if both entries reference the same aggressor instance
     */
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

    /**
     * Debug representation of this aggro entry.
     * @return a human-readable representation of aggressor, hate and damage
     */
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
