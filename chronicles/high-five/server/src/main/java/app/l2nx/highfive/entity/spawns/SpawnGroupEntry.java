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
package app.l2nx.highfive.entity.spawns;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * A single NPC entry of a spawn group, holding its quota and one spawn slot per territory.
 * @author Altur
 */
public class SpawnGroupEntry {
    @Getter
    private final int total;

    @Getter
    private final int respawnDelay;

    @Getter
    private final int respawnRandom;

    @Getter
    private final List<Spawn> slots = new ArrayList<>();

    @Getter
    @Setter
    private int aliveOrReserved = 0;

    public SpawnGroupEntry(int total, int respawnDelay, int respawnRandom) {
        this.total = total;
        this.respawnDelay = respawnDelay;
        this.respawnRandom = respawnRandom;
    }

    public void addSlot(Spawn spawn) {
        slots.add(spawn);
    }
}
