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
package app.l2nx.highfive.entity.actor.stat;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.partition.WorldPartition;

@SingleWriter
public class NpcStat extends CreatureStat {
    public NpcStat(Npc activeChar, CreatureAccess access) {
        super(activeChar, access);
    }

    @Override
    protected byte level() {
        return ((NpcTemplate) template()).getLevel();
    }

    @Override
    public Npc getActiveChar() {
        WorldPartition.checkOwner();
        return npc();
    }

    @Override
    protected int physicalAttackAngle() {
        return npc().getTemplate().getBaseAttackAngle();
    }

    private Npc npc() {
        return (Npc) creature();
    }
}
