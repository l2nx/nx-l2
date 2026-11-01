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

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class ExStorageMaxCount extends ServerPacket {
    private final int inventory;
    private final int warehouse;
    private final int clan;
    private final int privateSell;
    private final int privateBuy;
    private final int receipeD;
    private final int recipe;
    private final int inventoryExtraSlots;
    private final int inventoryQuestItems;

    public ExStorageMaxCount(Player player) {
        inventory = player.getInventoryLimit();
        warehouse = player.getWareHouseLimit();
        privateSell = player.getPrivateSellStoreLimit();
        privateBuy = player.getPrivateBuyStoreLimit();
        clan = PlayerConfig.WAREHOUSE_SLOTS_CLAN;
        receipeD = player.getDwarfRecipeLimit();
        recipe = player.getCommonRecipeLimit();
        inventoryExtraSlots = (int) player.calcStat(Stat.INV_LIM, 0, null, null);
        inventoryQuestItems = PlayerConfig.INVENTORY_MAXIMUM_QUEST_ITEMS;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_STORAGE_MAX_COUNT.writeId(this, buffer);
        buffer.writeInt(inventory);
        buffer.writeInt(warehouse);
        buffer.writeInt(clan);
        buffer.writeInt(privateSell);
        buffer.writeInt(privateBuy);
        buffer.writeInt(receipeD);
        buffer.writeInt(recipe);
        buffer.writeInt(inventoryExtraSlots);
        buffer.writeInt(inventoryQuestItems);
    }
}
