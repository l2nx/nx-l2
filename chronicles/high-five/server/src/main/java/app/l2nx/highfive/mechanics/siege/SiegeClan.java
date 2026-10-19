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
package app.l2nx.highfive.mechanics.siege;

import app.l2nx.highfive.entity.actor.Npc;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

public class SiegeClan {
    @Getter
    private int clanId = 0;

    @Getter
    private final Set<Npc> flag = ConcurrentHashMap.newKeySet();

    @Getter
    @Setter
    private SiegeClanType type;

    public SiegeClan(int clanId, SiegeClanType type) {
        this.clanId = clanId;
        this.type = type;
    }

    public int getNumFlags() {
        return flag.size();
    }

    public void addFlag(Npc flag) {
        this.flag.add(flag);
    }

    public boolean removeFlag(Npc flag) {
        final boolean ret = this.flag.remove(flag);
        flag.deleteMe();
        return ret;
    }

    public void removeFlags() {
        flag.forEach(Npc::decayMe);
        flag.clear();
    }
}
