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

import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.instance.SiegeFlag;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.residences.ClanHall;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.listeners.AbstractEventListener;
import app.l2nx.highfive.mechanics.script.Event;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.SiegeClan;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * @version $Revision: 1.7.2.3.2.6 $ $Date: 2005/03/27 15:29:30 $
 */
@Slf4j
public class RequestRestartPoint extends ClientPacket {
    protected int requestedPointType;
    protected boolean continuation;

    @Override
    protected void readImpl() {
        requestedPointType = app.l2nx.protocol.game.p267.clientpackets.RequestRestartPoint.decode(frame())
                .pointType();
    }

    class DeathTask implements Runnable {
        final Player player;

        DeathTask(Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            portPlayer(player);
        }
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (!player.canRevive()) {
            return;
        }

        if (player.isFakeDeath()) {
            player.stopFakeDeath(true);
            return;
        } else if (!player.isDead()) {
            return;
        }

        // Custom event resurrection management.
        if (player.isOnEvent()) {
            for (AbstractEventListener listener : player.getListeners(EventType.ON_CREATURE_DEATH)) {
                if (listener.getOwner() instanceof Event) {
                    ((Event) listener.getOwner()).notifyEvent("ResurrectPlayer", null, player);
                    return;
                }
            }
        }

        final Castle castle = CastleManager.getInstance().getCastle(player.getX(), player.getY(), player.getZ());
        if ((castle != null)
                && castle.getSiege().isInProgress()
                && (player.getClan() != null)
                && castle.getSiege().checkIsAttacker(player.getClan())) {
            // Schedule respawn delay for attacker.
            WorldPartition.loop()
                    .schedule(
                            new DeathTask(player), castle.getSiege().getAttackerRespawnDelay(), TimeUnit.MILLISECONDS);
            if (castle.getSiege().getAttackerRespawnDelay() > 0) {
                player.sendMessage("You will be re-spawned in "
                        + (castle.getSiege().getAttackerRespawnDelay() / 1000) + " seconds");
            }
            return;
        }

        portPlayer(player);
    }

    protected void portPlayer(Player player) {
        Location loc = null;
        Castle castle = null;
        Fort fort = null;
        SiegableHall hall = null;
        final boolean isInDefense = false;
        int instanceId = 0;

        // force jail
        if (player.isJailed()) {
            requestedPointType = 27;
        } else if (player.isFestivalParticipant()) {
            requestedPointType = 5;
        }

        final Clan clan = player.getClan();
        switch (requestedPointType) {
            case 1: // to clanhall
            {
                if ((clan == null) || (clan.getHideoutId() == 0)) {
                    log.warn(
                            "Player [{}] called RestartPointPacket - To Clanhall and he doesn't have Clanhall!",
                            player.getName());
                    return;
                }

                loc = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.CLANHALL);
                if ((ClanHallTable.getInstance().getClanHallByOwner(clan) != null)
                        && (ClanHallTable.getInstance().getClanHallByOwner(clan).getFunction(ClanHall.FUNC_RESTORE_EXP)
                                != null)) {
                    player.restoreExp(ClanHallTable.getInstance()
                            .getClanHallByOwner(clan)
                            .getFunction(ClanHall.FUNC_RESTORE_EXP)
                            .getLevel());
                }
                break;
            }
            case 2: // to castle
            {
                castle = CastleManager.getInstance().getCastle(player);
                if ((castle != null) && castle.getSiege().isInProgress()) {
                    // Siege in progress
                    if (castle.getSiege().checkIsDefender(clan)) {
                        loc = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.CASTLE);
                    } else if (castle.getSiege().checkIsAttacker(clan)) {
                        loc = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.TOWN);
                    } else {
                        log.warn(
                                "Player [{}] called RestartPointPacket - To Castle and he doesn't have Castle!",
                                player.getName());
                        return;
                    }
                } else {
                    if ((clan == null) || (clan.getCastleId() == 0)) {
                        return;
                    }

                    loc = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.CASTLE);
                }

                if ((CastleManager.getInstance().getCastleByOwner(clan) != null)
                        && (CastleManager.getInstance().getCastleByOwner(clan).getFunction(Castle.FUNC_RESTORE_EXP)
                                != null)) {
                    player.restoreExp(CastleManager.getInstance()
                            .getCastleByOwner(clan)
                            .getFunction(Castle.FUNC_RESTORE_EXP)
                            .getLevel());
                }
                break;
            }
            case 3: // to fortress
            {
                if (((clan == null) || (clan.getFortId() == 0)) && !isInDefense) {
                    log.warn(
                            "Player [{}] called RestartPointPacket - To Fortress and he doesn't have Fortress!",
                            player.getName());
                    return;
                }

                loc = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.FORTRESS);
                if ((FortManager.getInstance().getFortByOwner(clan) != null)
                        && (FortManager.getInstance().getFortByOwner(clan).getFunction(Fort.FUNC_RESTORE_EXP)
                                != null)) {
                    player.restoreExp(FortManager.getInstance()
                            .getFortByOwner(clan)
                            .getFunction(Fort.FUNC_RESTORE_EXP)
                            .getLevel());
                }
                break;
            }
            case 4: // to siege HQ
            {
                SiegeClan siegeClan = null;
                castle = CastleManager.getInstance().getCastle(player);
                fort = FortManager.getInstance().getFort(player);
                hall = CHSiegeManager.getInstance().getNearbyClanHall(player);
                SiegeFlag flag = TerritoryWarManager.getInstance().getHQForClan(clan);
                if (flag == null) {
                    flag = TerritoryWarManager.getInstance().getFlagForClan(clan);
                }

                if ((castle != null) && castle.getSiege().isInProgress()) {
                    siegeClan = castle.getSiege().getAttackerClan(clan);
                } else if ((fort != null) && fort.getSiege().isInProgress()) {
                    siegeClan = fort.getSiege().getAttackerClan(clan);
                } else if ((hall != null) && hall.isInSiege()) {
                    siegeClan = hall.getSiege().getAttackerClan(clan);
                }

                if (((siegeClan == null) || siegeClan.getFlag().isEmpty()) && (flag == null)) {
                    // Check if clan hall has inner spawns loc.
                    if (hall != null) {
                        loc = hall.getSiege().getInnerSpawnLoc(player);
                        if (loc != null) {
                            break;
                        }
                    }

                    log.warn(
                            "Player [{}] called RestartPointPacket - To Siege HQ and he doesn't have Siege HQ!",
                            player.getName());
                    return;
                }

                loc = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.SIEGEFLAG);
                break;
            }
            case 5: // Fixed or Player is a festival participant.
            {
                if (!player.isGM()
                        && !player.isFestivalParticipant()
                        && !player.getInventory().haveItemForSelfResurrection()) {
                    log.warn(
                            "Player [{}] called RestartPointPacket - Fixed and he isn't festival participant!",
                            player.getName());
                    return;
                }

                if (player.isGM()
                        || player.destroyItemByItemId(ItemProcessType.FEE, 10649, 1, player, false)
                        || player.destroyItemByItemId(ItemProcessType.FEE, 13300, 1, player, false)
                        || player.destroyItemByItemId(ItemProcessType.FEE, 13128, 1, player, false)) {
                    player.doRevive(100.00);
                } else
                // Festival Participant
                {
                    instanceId = player.getInstanceId();
                    loc = new Location(player);
                }
                break;
            }
            case 6: // TODO: agathion ress
            {
                break;
            }
            case 27: // to jail
            {
                if (!player.isJailed()) {
                    return;
                }

                loc = new Location(-114356, -249645, -2984);
                break;
            }
            default: {
                loc = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.TOWN);
                break;
            }
        }

        // Teleport and revive
        if (loc != null) {
            player.setInstanceId(instanceId);
            player.setIn7sDungeon(false);
            player.setIsPendingRevive(true);
            player.teleToLocation(loc, true);
        }
    }
}
