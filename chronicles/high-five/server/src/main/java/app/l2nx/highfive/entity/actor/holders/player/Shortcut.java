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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.partition.WorldPartition;

@SingleWriter
public class Shortcut {
    /** Slot from 0 to 11. */
    private final int slot;
    /** Page from 0 to 9. */
    private final int page;

    private final ShortcutType type;
    private final int id;
    private final int level;
    /** Character type: 1 player, 2 summon. */
    private final int characterType;

    private int sharedReuseGroup = -1;

    public Shortcut(int slot, int page, ShortcutType type, int id, int level, int characterType) {
        this.slot = slot;
        this.page = page;
        this.type = type;
        this.id = id;
        this.level = level;
        this.characterType = characterType;
    }

    public int getSlot() {
        WorldPartition.checkOwner();
        return slot;
    }

    public int getPage() {
        WorldPartition.checkOwner();
        return page;
    }

    public ShortcutType getType() {
        WorldPartition.checkOwner();
        return type;
    }

    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public int getLevel() {
        WorldPartition.checkOwner();
        return level;
    }

    public int getCharacterType() {
        WorldPartition.checkOwner();
        return characterType;
    }

    public int getSharedReuseGroup() {
        WorldPartition.checkOwner();
        return sharedReuseGroup;
    }

    public void setSharedReuseGroup(int sharedReuseGroup) {
        WorldPartition.checkOwner();
        this.sharedReuseGroup = sharedReuseGroup;
    }
}
