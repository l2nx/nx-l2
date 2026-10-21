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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.auction.ItemAuction;
import app.l2nx.highfive.entity.item.auction.ItemAuctionInstance;
import app.l2nx.highfive.handler.IBypassHandler;
import app.l2nx.highfive.managers.ItemAuctionManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExItemAuctionInfoPacket;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ItemAuctionLink implements IBypassHandler {
    private static final DateTimeFormatter SDF = DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy");

    private static final String[] COMMANDS = {"ItemAuction"};

    @Override
    public boolean onCommand(String command, Player player, Creature target) {
        if (!target.isNpc()) {
            return false;
        }

        if (!GeneralConfig.ALT_ITEM_AUCTION_ENABLED) {
            player.sendPacket(SystemMessageId.IT_IS_NOT_AN_AUCTION_PERIOD);
            return true;
        }

        final ItemAuctionInstance au = ItemAuctionManager.getInstance().getManagerInstance(target.getId());
        if (au == null) {
            return false;
        }

        try {
            final StringTokenizer st = new StringTokenizer(command);
            st.nextToken(); // bypass "ItemAuction"
            if (!st.hasMoreTokens()) {
                return false;
            }

            final String cmd = st.nextToken();
            if ("show".equalsIgnoreCase(cmd)) {
                if (!player.getClient().getFloodProtectors().canUseItemAuction()) {
                    return false;
                }

                if (player.isItemAuctionPolling()) {
                    return false;
                }

                final ItemAuction currentAuction = au.getCurrentAuction();
                final ItemAuction nextAuction = au.getNextAuction();
                if (currentAuction == null) {
                    player.sendPacket(SystemMessageId.IT_IS_NOT_AN_AUCTION_PERIOD);

                    if (nextAuction != null) {
                        player.sendMessage("The next auction will begin on the "
                                + SDF.format(Instant.ofEpochMilli(nextAuction.getStartingTime())
                                        .atZone(ClockBridge.wallClock().getZone()))
                                + ".");
                    }

                    return true;
                }

                player.sendPacket(new ExItemAuctionInfoPacket(false, currentAuction, nextAuction));
            } else if ("cancel".equalsIgnoreCase(cmd)) {
                boolean returned = false;
                for (ItemAuction auction : au.getAuctionsByBidder(player.getObjectId())) {
                    if (auction.cancelBid(player)) {
                        returned = true;
                    }
                }

                if (!returned) {
                    player.sendPacket(SystemMessageId.THERE_ARE_NO_OFFERINGS_I_OWN_OR_I_MADE_A_BID_FOR);
                }
            } else {
                return false;
            }
        } catch (Exception e) {
            log.warn("Bypass failed", e);
        }

        return true;
    }

    @Override
    public String[] getCommandList() {
        return COMMANDS;
    }
}
