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
package app.l2nx.highfive.entity.actor.holders.creature;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * Simple class containing all necessary information to maintain<br>
 * valid time stamps and reuse for skills and items reuse upon re-login.<br>
 * <b>Filter this carefully as it becomes redundant to store reuse for small delays.</b>
 * @author Yesod, Zoey76, Mobius
 */
@SingleWriter
public class TimeStamp {
    private final int id1;
    private final int id2;

    private final long reuse;

    private long stamp;

    private final int sharedReuseGroup;

    public TimeStamp(Skill skill, long reuse, long endsAt) {
        id1 = skill.getId();
        id2 = skill.getLevel();
        this.reuse = reuse;
        stamp = endsAt > 0 ? endsAt : reuse != 0 ? ClockBridge.gameMillis() + reuse : 0;
        sharedReuseGroup = -1;
    }

    public TimeStamp(Item item, long reuse, long endsAt) {
        id1 = item.getId();
        id2 = item.getObjectId();
        this.reuse = reuse;
        stamp = endsAt > 0 ? endsAt : reuse != 0 ? ClockBridge.gameMillis() + reuse : 0;
        sharedReuseGroup = item.getSharedReuseGroup();
    }

    public int getItemId() {
        WorldPartition.checkOwner();
        return id1;
    }

    public int getItemObjectId() {
        WorldPartition.checkOwner();
        return id2;
    }

    public int getSkillId() {
        WorldPartition.checkOwner();
        return id1;
    }

    public int getSkillLevel() {
        WorldPartition.checkOwner();
        return id2;
    }

    public long getReuse() {
        WorldPartition.checkOwner();
        return reuse;
    }

    public long getStamp() {
        WorldPartition.checkOwner();
        return stamp;
    }

    public int getSharedReuseGroup() {
        WorldPartition.checkOwner();
        return sharedReuseGroup;
    }

    public long getRemaining() {
        WorldPartition.checkOwner();
        if (stamp == 0) {
            return 0;
        }

        final long remainingTime = stamp - ClockBridge.gameMillis();
        if (remainingTime <= 0) {
            stamp = 0;
            return 0;
        }

        return remainingTime;
    }

    public boolean hasNotPassed() {
        WorldPartition.checkOwner();
        if (stamp == 0) {
            return false;
        }

        if (ClockBridge.gameMillis() >= stamp) {
            stamp = 0;
            return false;
        }

        return true;
    }
}
