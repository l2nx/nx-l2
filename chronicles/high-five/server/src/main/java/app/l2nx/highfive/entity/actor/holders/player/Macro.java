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

import java.util.List;
import lombok.Getter;

/**
 * Immutable, so the presets of the initial shortcut data can sit in every player's macro list.
 */
public class Macro {
    @Getter
    private final int id;

    @Getter
    private final int icon;

    @Getter
    private final String name;

    @Getter
    private final String descr;

    @Getter
    private final String acronym;

    @Getter
    private final List<MacroCmd> commands;

    public Macro(int id, int icon, String name, String descr, String acronym, List<MacroCmd> list) {
        this.id = id;
        this.icon = icon;
        this.name = name;
        this.descr = descr;
        this.acronym = acronym;
        commands = List.copyOf(list);
    }

    public Macro withId(int newId) {
        return new Macro(newId, icon, name, descr, acronym, commands);
    }
}
