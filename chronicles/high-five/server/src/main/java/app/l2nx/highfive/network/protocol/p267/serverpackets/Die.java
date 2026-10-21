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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.data.holders.AccessLevel;
import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.SiegeClan;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class Die extends ServerPacket {
    private final int objectId;
    private final boolean canTeleport;
    private final boolean sweepable;
    private AccessLevel access = AdminData.getInstance().getAccessLevel(0);
    private Clan clan;
    private final Creature creature;
    private boolean jailed;
    private boolean staticRes = false;

    public Die(Creature creature) {
        objectId = creature.getObjectId();
        this.creature = creature;
        if (creature.isPlayer()) {
            final Player player = creature.asPlayer();
            access = player.getAccessLevel();
            clan = player.getClan();
            jailed = player.isJailed();
        }

        canTeleport = creature.canRevive() && !creature.isPendingRevive();
        sweepable = creature.isSweepActive();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.DIE.writeId(this, buffer);
        buffer.writeInt(objectId);
        buffer.writeInt(canTeleport);
        if (creature.isPlayer()) {
            final Player player = creature.asPlayer();
            if (!OlympiadManager.getInstance().isRegistered(player) && !player.isOnEvent()) {
                staticRes = creature.getInventory().haveItemForSelfResurrection();
            }

            // Verify if player can use fixed resurrection without Feather.
            if (access.allowFixedRes()) {
                staticRes = true;
            }
        }

        if (canTeleport && (clan != null) && !jailed) {
            boolean isInCastleDefense = false;
            boolean isInFortDefense = false;
            SiegeClan siegeClan = null;
            final Castle castle = CastleManager.getInstance().getCastle(creature);
            final Fort fort = FortManager.getInstance().getFort(creature);
            final SiegableHall hall = CHSiegeManager.getInstance().getNearbyClanHall(creature);
            if ((castle != null) && castle.getSiege().isInProgress()) {
                // siege in progress
                siegeClan = castle.getSiege().getAttackerClan(clan);
                if ((siegeClan == null) && castle.getSiege().checkIsDefender(clan)) {
                    isInCastleDefense = true;
                }
            } else if ((fort != null) && fort.getSiege().isInProgress()) {
                // siege in progress
                siegeClan = fort.getSiege().getAttackerClan(clan);
                if ((siegeClan == null) && fort.getSiege().checkIsDefender(clan)) {
                    isInFortDefense = true;
                }
            }

            buffer.writeInt(clan.getHideoutId() > 0); // 6d 01 00 00 00 - to hide away
            buffer.writeInt((clan.getCastleId() > 0) || isInCastleDefense); // 6d 02 00 00 00 - to castle
            buffer.writeInt((TerritoryWarManager.getInstance().getHQForClan(clan) != null)
                    || ((siegeClan != null)
                            && !isInCastleDefense
                            && !isInFortDefense
                            && !siegeClan.getFlag().isEmpty())
                    || ((hall != null)
                            && (hall.getSiege() != null)
                            && hall.getSiege().checkIsAttacker(clan))); // siege HQ
            buffer.writeInt(sweepable); // sweepable (blue glow)
            buffer.writeInt(staticRes); // 6d 04 00 00 00 - to FIXED
            buffer.writeInt((clan.getFortId() > 0) || isInFortDefense); // 6d 05 00 00 00 - to fortress
        } else {
            buffer.writeInt(0); // 6d 01 00 00 00 - to hide away
            buffer.writeInt(0); // 6d 02 00 00 00 - to castle
            buffer.writeInt(0); // 6d 03 00 00 00 - to siege HQ
            buffer.writeInt(sweepable); // sweepable (blue glow)
            buffer.writeInt(staticRes); // 6d 04 00 00 00 - to FIXED
            buffer.writeInt(0); // 6d 05 00 00 00 - to fortress
        }

        // TODO: protocol 152
        // buffer.writeByte(0); // show die animation
        // buffer.writeInt(0); // agathion ress button
        // buffer.writeInt(0); // additional free space
    }
}
