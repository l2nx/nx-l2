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

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PrivateStoreMsgSell;

/**
 * @version $Revision: 1.2.4.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class SetPrivateStoreMsgSell extends ClientPacket {
    private static final int MAX_MSG_LENGTH = 29;

    private String storeMsg;

    @Override
    protected void readImpl() {
        storeMsg = readString();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if ((player == null) || (player.getSellList() == null)) {
            return;
        }

        if ((storeMsg != null) && (storeMsg.length() > MAX_MSG_LENGTH)) {
            PunishmentManager.handleIllegalPlayerAction(
                    player, player + " tried to overflow private store sell message", GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        player.getSellList().setTitle(storeMsg);
        player.sendPacket(new PrivateStoreMsgSell(player));
    }
}
