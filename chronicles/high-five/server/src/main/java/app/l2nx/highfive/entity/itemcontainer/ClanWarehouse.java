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

import app.l2nx.engine.core.loop.SingleWriter;
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
import app.l2nx.highfive.partition.WorldPartition;

@SingleWriter
public class ClanWarehouse extends Warehouse {
    private final Clan clan;

    public ClanWarehouse(Clan clan) {
        this.clan = clan;
    }

    @Override
    public String getName() {
        WorldPartition.checkOwner();
        return "ClanWarehouse";
    }

    @Override
    public int getOwnerId() {
        WorldPartition.checkOwner();
        return clan.getId();
    }

    @Override
    public Player getOwner() {
        WorldPartition.checkOwner();
        return clan.getLeader().getPlayer();
    }

    @Override
    public ItemLocation getBaseLocation() {
        WorldPartition.checkOwner();
        return ItemLocation.CLANWH;
    }

    public String getLocationId() {
        WorldPartition.checkOwner();
        return "0";
    }

    public int getLocationId(boolean dummy) {
        WorldPartition.checkOwner();
        return 0;
    }

    public void setLocationId(Player dummy) {
        WorldPartition.checkOwner();
    }

    @Override
    public boolean validateCapacity(long slots) {
        WorldPartition.checkOwner();
        return (liveItems().size() + slots) <= PlayerConfig.WAREHOUSE_SLOTS_CLAN;
    }

    @Override
    public Item addItem(ItemProcessType process, int itemId, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item item = super.addItem(process, itemId, count, actor, reference);

        if ((item != null)
                && EventDispatcher.getInstance()
                        .hasListener(EventType.ON_PLAYER_CLAN_WH_ITEM_ADD, item.getTemplate())) {
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
        WorldPartition.checkOwner();
        // A stackable item merges into the stack already held, so the event carries the warehouse's item.
        final Item added = super.addItem(process, item, actor, reference);
        if ((added != null)
                && EventDispatcher.getInstance()
                        .hasListener(EventType.ON_PLAYER_CLAN_WH_ITEM_ADD, added.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerClanWHItemAdd.builder()
                                    .player(actor)
                                    .item(added)
                                    .container(this)
                                    .build(),
                            added.getTemplate());
        }

        return added;
    }

    @Override
    public Item destroyItem(ItemProcessType process, Item item, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item destroyed = super.destroyItem(process, item, count, actor, reference);
        if ((destroyed != null)
                && EventDispatcher.getInstance()
                        .hasListener(EventType.ON_PLAYER_CLAN_WH_ITEM_DESTROY, destroyed.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerClanWHItemDestroy.builder()
                                    .player(actor)
                                    .item(destroyed)
                                    .count(count)
                                    .container(this)
                                    .build(),
                            destroyed.getTemplate());
        }

        return destroyed;
    }

    @Override
    public Item transferItem(
            ItemProcessType process, int objectId, long count, ItemContainer target, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item item = getItemByObjectId(objectId);
        if (item == null) {
            return null;
        }

        // Read before the transfer: it caps the count at what the item holds and may destroy the item.
        final long moved = Math.min(count, item.getCount());
        final Item transferred = super.transferItem(process, objectId, count, target, actor, reference);
        if ((transferred != null)
                && EventDispatcher.getInstance()
                        .hasListener(EventType.ON_PLAYER_CLAN_WH_ITEM_TRANSFER, item.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnPlayerClanWHItemTransfer.builder()
                                    .player(actor)
                                    .item(item)
                                    .count(moved)
                                    .container(target)
                                    .build(),
                            item.getTemplate());
        }

        return transferred;
    }
}
