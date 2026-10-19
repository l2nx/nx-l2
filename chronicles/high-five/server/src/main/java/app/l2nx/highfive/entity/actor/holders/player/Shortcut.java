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

import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import lombok.Getter;
import lombok.Setter;

public class Shortcut {
    /** Slot from 0 to 11. */
    @Getter
    private final int slot;
    /** Page from 0 to 9. */
    @Getter
    private final int page;
    /** Type: item, skill, action, macro, recipe, bookmark. */
    @Getter
    private final ShortcutType type;
    /** Shortcut ID. */
    @Getter
    private final int id;
    /** Shortcut level (skills). */
    @Getter
    private final int level;
    /** Character type: 1 player, 2 summon. */
    @Getter
    private final int characterType;
    /** Shared reuse group. */
    @Getter
    @Setter
    private int sharedReuseGroup = -1;

    public Shortcut(int slot, int page, ShortcutType type, int id, int level, int characterType) {
        this.slot = slot;
        this.page = page;
        this.type = type;
        this.id = id;
        this.level = level;
        this.characterType = characterType;
    }
}
