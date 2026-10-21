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

import app.l2nx.highfive.data.xml.HennaData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Henna;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.HennaItemRemoveInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Zoey76
 */
@Slf4j
public class RequestHennaItemRemoveInfo extends ClientPacket {
    private int symbolId;

    @Override
    protected void readImpl() {
        symbolId = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if ((player == null) || (symbolId == 0)) {
            return;
        }

        final Henna henna = HennaData.getInstance().getHenna(symbolId);
        if (henna == null) {
            log.warn("{}: Invalid Henna Id: {} from {}", getClass().getName(), symbolId, player);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        player.sendPacket(new HennaItemRemoveInfo(henna, player));
    }
}
