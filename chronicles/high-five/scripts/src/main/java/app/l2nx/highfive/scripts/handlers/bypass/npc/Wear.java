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
package app.l2nx.highfive.scripts.handlers.bypass.npc;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.xml.BuyListData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IBypassHandler;
import app.l2nx.highfive.mechanics.buylist.BuyListHolder;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ShopPreviewList;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Wear implements IBypassHandler {
    private static final String[] COMMANDS = {"Wear"};

    @Override
    public boolean onCommand(String command, Player player, Creature target) {
        if (!target.isNpc()) {
            return false;
        }

        if (!GeneralConfig.ALLOW_WEAR) {
            return false;
        }

        try {
            final StringTokenizer st = new StringTokenizer(command, " ");
            st.nextToken();

            if (st.countTokens() < 1) {
                return false;
            }

            showWearWindow(player, Integer.parseInt(st.nextToken()));
            return true;
        } catch (Exception e) {
            log.warn("Bypass failed", e);
        }

        return false;
    }

    private void showWearWindow(Player player, int value) {
        final BuyListHolder buyList = BuyListData.getInstance().getBuyList(value);
        if (buyList == null) {
            log.warn("BuyList not found! BuyListId:{}", value);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        player.setInventoryBlockingStatus(true);

        player.sendPacket(new ShopPreviewList(buyList, player.getAdena(), player.getExpertiseLevel()));
    }

    @Override
    public String[] getCommandList() {
        return COMMANDS;
    }
}
