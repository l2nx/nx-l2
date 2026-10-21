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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.mechanics.sevensigns.SevenSigns;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * Changes the sky color depending on the outcome of the Seven Signs competition.
 * @author Tempy
 */
public class SSQInfo extends ServerPacket {
    private int state = 0;

    public SSQInfo() {
        final int compWinner = SevenSigns.getInstance().getCabalHighestScore();
        if (SevenSigns.getInstance().isSealValidationPeriod()) {
            if (compWinner == SevenSigns.CABAL_DAWN) {
                state = 2;
            } else if (compWinner == SevenSigns.CABAL_DUSK) {
                state = 1;
            }
        }
    }

    public SSQInfo(int state) {
        this.state = state;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.SSQ_INFO.writeId(this, buffer);
        buffer.writeShort(256 + state);
    }
}
