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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.managers.TerritoryWarManager.Territory;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.List;

/**
 * @author GodKratos
 */
public class ExShowDominionRegistry extends ServerPacket {
    private static final int MINID = 80;

    private final int castleId;
    private int clanReq = 0;
    private int mercReq = 0;
    private boolean mercRegistered = false;
    private boolean clanRegistered = false;
    private int warTime = (int) (ClockBridge.wallMillis() / 1000);
    private final int currentTime = (int) (ClockBridge.wallMillis() / 1000);

    public ExShowDominionRegistry(int castleId, Player player) {
        this.castleId = castleId;
        if (TerritoryWarManager.getInstance().getRegisteredClans(castleId) != null) {
            clanReq = TerritoryWarManager.getInstance()
                    .getRegisteredClans(castleId)
                    .size();

            final Clan clan = player.getClan();
            if (clan != null) {
                clanRegistered = (TerritoryWarManager.getInstance()
                        .getRegisteredClans(castleId)
                        .contains(clan));
            }
        }

        if (TerritoryWarManager.getInstance().getRegisteredMercenaries(castleId) != null) {
            mercReq = TerritoryWarManager.getInstance()
                    .getRegisteredMercenaries(castleId)
                    .size();
            mercRegistered = (TerritoryWarManager.getInstance()
                    .getRegisteredMercenaries(castleId)
                    .contains(player.getObjectId()));
        }

        warTime = (int) (TerritoryWarManager.getInstance().getTWStartTimeInMillis() / 1000);
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_SHOW_DOMINION_REGISTRY.writeId(this, buffer);
        buffer.writeInt(MINID + castleId); // Current Territory Id
        if (TerritoryWarManager.getInstance().getTerritory(castleId) == null) {
            // something is wrong
            buffer.writeString("No Owner"); // Owners Clan
            buffer.writeString("No Owner"); // Owner Clan Leader
            buffer.writeString("No Ally"); // Owner Alliance
        } else {
            final Clan clan =
                    TerritoryWarManager.getInstance().getTerritory(castleId).getOwnerClan();
            if (clan == null) {
                // something is wrong
                buffer.writeString("No Owner"); // Owners Clan
                buffer.writeString("No Owner"); // Owner Clan Leader
                buffer.writeString("No Ally"); // Owner Alliance
            } else {
                buffer.writeString(clan.getName()); // Owners Clan
                buffer.writeString(clan.getLeaderName()); // Owner Clan Leader
                buffer.writeString(clan.getAllyName()); // Owner Alliance
            }
        }

        buffer.writeInt(clanReq); // Clan Request
        buffer.writeInt(mercReq); // Merc Request
        buffer.writeInt(warTime); // War Time
        buffer.writeInt(currentTime); // Current Time
        buffer.writeInt(clanRegistered); // is Cancel clan registration
        buffer.writeInt(mercRegistered); // is Cancel mercenaries registration
        buffer.writeInt(1); // unknown
        final List<Territory> territoryList = TerritoryWarManager.getInstance().getAllTerritories();
        buffer.writeInt(territoryList.size()); // Territory Count
        for (Territory t : territoryList) {
            buffer.writeInt(t.getTerritoryId()); // Territory Id
            buffer.writeInt(t.getOwnedWardIds().size()); // Emblem Count
            for (int i : t.getOwnedWardIds()) {
                buffer.writeInt(i); // Emblem ID - should be in for loop for emblem count
            }
        }
    }
}
