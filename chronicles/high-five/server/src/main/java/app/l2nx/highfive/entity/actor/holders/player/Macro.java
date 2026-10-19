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
import lombok.Setter;

public class Macro {
    @Getter
    @Setter
    private int id;

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

    /**
     * Constructor for macros.
     * @param id the macro ID
     * @param icon the icon ID
     * @param name the macro name
     * @param descr the macro description
     * @param acronym the macro acronym
     * @param list the macro command list
     */
    public Macro(int id, int icon, String name, String descr, String acronym, List<MacroCmd> list) {
        this.id = id;
        this.icon = icon;
        this.name = name;
        this.descr = descr;
        this.acronym = acronym;
        commands = list;
    }
}
