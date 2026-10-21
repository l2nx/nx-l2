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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.Henna;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestHennaRemove extends ClientPacket {
    private int symbolId;

    @Override
    protected void readImpl() {
        symbolId = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (!getClient().getFloodProtectors().canPerformTransaction()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        Henna henna;
        boolean found = false;
        for (int i = 1; i <= 3; i++) {
            henna = player.getHenna(i);
            if ((henna != null) && (henna.getDyeId() == symbolId)) {
                if (player.getAdena() >= henna.getCancelFee()) {
                    player.removeHenna(i);
                } else {
                    player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
                    player.sendPacket(ActionFailed.STATIC_PACKET);
                }

                found = true;
                break;
            }
        }

        if (!found) {
            log.warn(
                    "{}: {} requested Henna Draw remove without any henna.",
                    getClass().getSimpleName(),
                    player);
            player.sendPacket(ActionFailed.STATIC_PACKET);
        }
    }
}
