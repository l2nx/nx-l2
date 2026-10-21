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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.highfive.data.MerchantPriceConfigTable;
import app.l2nx.highfive.data.MerchantPriceConfigTable.MerchantPriceConfig;
import app.l2nx.highfive.data.xml.BuyListData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.mechanics.buylist.BuyListHolder;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.BuyList;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExBuySellList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @version $Revision: 1.10.4.9 $ $Date: 2005/04/11 10:06:08 $
 */
@Slf4j
public class Merchant extends Folk {
    @Getter
    private MerchantPriceConfig mpc;

    /**
     * Creates a merchant,
     * @param template the merchant NPC template
     */
    public Merchant(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.Merchant);
    }

    @Override
    public void onSpawn() {
        super.onSpawn();
        mpc = MerchantPriceConfigTable.getInstance().getMerchantPriceConfig(this);
    }

    @Override
    public String getHtmlPath(int npcId, int value) {
        String pom = "";
        if (value == 0) {
            pom = Integer.toString(npcId);
        } else {
            pom = npcId + "-" + value;
        }

        return "data/html/merchant/" + pom + ".htm";
    }

    public void showBuyWindow(Player player, int value) {
        showBuyWindow(player, value, true);
    }

    public void showBuyWindow(Player player, int value, boolean applyTax) {
        final BuyListHolder buyList = BuyListData.getInstance().getBuyList(value);
        if (buyList == null) {
            log.warn("BuyList not found! BuyListId:{}", value);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (!buyList.isNpcAllowed(getId())) {
            log.warn("Npc not allowed in BuyList! BuyListId:{} NpcId:{}", value, getId());
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final double taxRate = (applyTax) ? mpc.getTotalTaxRate() : 0;
        player.setInventoryBlockingStatus(true);

        player.sendPacket(new BuyList(buyList, player.getAdena(), taxRate));
        player.sendPacket(new ExBuySellList(player, false));
        player.sendPacket(ActionFailed.STATIC_PACKET);
    }
}
