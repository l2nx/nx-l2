/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.highfive.entity.actor.enums.player.MacroType;
import lombok.Getter;

/**
 * Macro Cmd DTO.
 * @author Zoey76
 */
public class MacroCmd {
    @Getter
    private final int entry;

    @Getter
    private final MacroType type;

    @Getter
    private final int d1; // skill_id or page for shortcuts

    @Getter
    private final int d2; // shortcut

    @Getter
    private final String cmd;

    public MacroCmd(int entry, MacroType type, int d1, int d2, String cmd) {
        this.entry = entry;
        this.type = type;
        this.d1 = d1;
        this.d2 = d2;
        this.cmd = cmd;
    }
}
