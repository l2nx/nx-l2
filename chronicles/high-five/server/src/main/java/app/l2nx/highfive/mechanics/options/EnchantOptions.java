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
package app.l2nx.highfive.mechanics.options;

import lombok.Getter;

/**
 * @author UnAfraid
 */
public class EnchantOptions {
    @Getter
    private final int level;

    @Getter
    private final int[] options;

    public EnchantOptions(int level) {
        this.level = level;
        options = new int[3];
    }

    public void setOption(byte index, int option) {
        if (options.length > index) {
            options[index] = option;
        }
    }
}
