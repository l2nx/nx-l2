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
package app.l2nx.highfive.mechanics.undergroundColiseum;

import app.l2nx.highfive.entity.groups.Party;
import lombok.Getter;

public class UCWaiting {
    private final Party party;

    @Getter
    private long registerMillis;

    @Getter
    private final UCArena baseArena;

    public UCWaiting(Party party, UCArena baseArena) {
        this.party = party;
        this.baseArena = baseArena;
    }

    public void clean() {
        registerMillis = 0L;
    }

    public Party getParty() {
        if ((party != null) && (party.getLeader() == null)) {
            setParty(false);
        }

        return party;
    }

    public void setParty(boolean isActive) {
        if (isActive) {
            party.setUCState(this);
        } else {
            party.setUCState(null);
        }
    }

    public void hasRegisterdNow() {
        registerMillis = System.currentTimeMillis();
    }
}
