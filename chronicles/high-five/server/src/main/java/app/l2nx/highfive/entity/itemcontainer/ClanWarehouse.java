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
package app.l2nx.highfive.entity.itemcontainer;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.clanwh.OnPlayerClanWHItemAdd;
import app.l2nx.highfive.mechanics.events.holders.actor.player.clanwh.OnPlayerClanWHItemDestroy;
import app.l2nx.highfive.mechanics.events.holders.actor.player.clanwh.OnPlayerClanWHItemTransfer;

public class ClanWarehouse extends Warehouse {
    private final Clan clan;

    public ClanWarehouse(Clan clan) {
        this.clan = clan;
    }

    @Override
    public String getName() {
        return "ClanWarehouse";
    }

    @Override
    public int getOwnerId() {
        return clan.getId();
    }

    @Override
    public Player getOwner() {
        return clan.getLeader().getPlayer();
    }

    @Override
    public ItemLocation getBaseLocation() {
        return ItemLocation.CLANWH;
    }

    public String getLocationId() {
        return "0";
    }

    public int getLocationId(boolean dummy) {
        return 0;
    }

    public void setLocationId(Player dummy) {}

    @Override
    public boolean validateCapacity(long slots) {
        return (items.size() + slots) <= PlayerConfig.WAREHOUSE_SLOTS_CLAN;
    }

    @Override
    public Item addItem(ItemProcessType process, int itemId, long count, Player actor, Object reference) {
        final Item item = super.addItem(process, itemId, count, actor, reference);

        // Notify to scripts
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_WH_ITEM_ADD, item.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerClanWHItemAdd.builder()
                                    .player(actor)
                                    .item(item)
                                    .container(this)
                                    .build(),
                            item.getTemplate());
        }

        return item;
    }

    @Override
    public Item addItem(ItemProcessType process, Item item, Player actor, Object reference) {
        // Notify to scripts
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_WH_ITEM_ADD, item.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerClanWHItemAdd.builder()
                                    .player(actor)
                                    .item(item)
                                    .container(this)
                                    .build(),
                            item.getTemplate());
        }

        return super.addItem(process, item, actor, reference);
    }

    @Override
    public Item destroyItem(ItemProcessType process, Item item, long count, Player actor, Object reference) {
        // Notify to scripts
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_WH_ITEM_DESTROY, item.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerClanWHItemDestroy.builder()
                                    .player(actor)
                                    .item(item)
                                    .count(count)
                                    .container(this)
                                    .build(),
                            item.getTemplate());
        }

        return super.destroyItem(process, item, count, actor, reference);
    }

    @Override
    public Item transferItem(
            ItemProcessType process, int objectId, long count, ItemContainer target, Player actor, Object reference) {
        final Item item = getItemByObjectId(objectId);

        // Notify to scripts
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_WH_ITEM_TRANSFER, item.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerClanWHItemTransfer.builder()
                                    .player(actor)
                                    .item(item)
                                    .count(count)
                                    .container(target)
                                    .build(),
                            item.getTemplate());
        }

        return super.transferItem(process, objectId, count, target, actor, reference);
    }
}
