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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

/**
 * Format: (c) ddd d: dx d: dy d: dz
 * @author -Wooden-
 */
public class MoveWithDelta extends ClientPacket {
    @SuppressWarnings("unused")
    private int dx;

    @SuppressWarnings("unused")
    private int dy;

    @SuppressWarnings("unused")
    private int dz;

    @Override
    protected void readImpl() {
        dx = readInt();
        dy = readInt();
        dz = readInt();
    }

    @Override
    protected void runImpl() {
        // TODO this
    }
}
