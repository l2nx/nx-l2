/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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

import app.l2nx.highfive.character.CharacterService;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.sql.OfflineTraderTable;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.instancezone.Instance;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.mechanics.variables.PlayerVariables;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RestartResponse;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 1.11.2.1.2.4 $ $Date: 2005/03/27 15:29:30 $
 */
@Slf4j
public class RequestRestart extends ClientPacket {

    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");

    @Override
    protected void readImpl() {}

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if ((player.getActiveEnchantItemId() != Player.ID_NONE)
                || (player.getActiveEnchantAttrItemId() != Player.ID_NONE)) {
            player.sendPacket(RestartResponse.valueOf(false));
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isChangingClass()) {
            log.warn("{} tried to restart during class change.", player);
            player.sendPacket(RestartResponse.valueOf(false));
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isInStoreMode()) {
            player.sendMessage("Cannot restart while trading.");
            player.sendPacket(RestartResponse.valueOf(false));
            return;
        }

        if (AttackStanceTaskManager.getInstance().hasAttackStanceTask(player)
                && !(player.isGM() && GeneralConfig.GM_RESTART_FIGHTING)) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_RESTART_WHILE_IN_COMBAT);
            player.sendPacket(RestartResponse.valueOf(false));
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Prevent player from restarting if they are a festival participant and it is in progress,
        // otherwise notify party members that the player is no longer a participant.
        if (player.isFestivalParticipant()) {
            if (SevenSignsFestival.getInstance().isFestivalInitialized()) {
                player.sendMessage("You cannot restart while you are a participant in a festival.");
                player.sendPacket(RestartResponse.valueOf(false));
                player.sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }

            final Party playerParty = player.getParty();
            if (playerParty != null) {
                player.getParty().broadcastString(player.getName() + " has been removed from the upcoming festival.");
            }
        }

        if (!player.canLogout()) {
            player.sendPacket(RestartResponse.valueOf(false));
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Remove player from boss zone.
        player.removeFromBossZone();

        // Unregister from olympiad.
        if (OlympiadManager.getInstance().isRegistered(player)) {
            OlympiadManager.getInstance().unRegisterNoble(player);
        }

        if (!GeneralConfig.RESTORE_PLAYER_INSTANCE) {
            final int instanceId = player.getInstanceId();
            if (instanceId > 0) {
                final Instance world = InstanceManager.getInstance().getInstance(instanceId);
                if (world != null) {
                    player.setInstanceId(0);
                    Location location = world.getExitLoc();
                    if (location == null) {
                        location = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.TOWN);
                    }

                    player.getVariables()
                            .set(
                                    PlayerVariables.RESTORE_LOCATION,
                                    location.getX() + ";" + location.getY() + ";" + location.getZ());
                    world.removePlayer(player.getObjectId());
                }
            }
        }

        final GameClient client = getClient();
        if (OfflineTraderTable.getInstance().enteredOfflineMode(player)) {
            ACCOUNTING_LOG.info("Entered offline mode, {}", client);
        } else {
            Disconnection.of(client, player).storeAndDelete();
        }

        client.sendPacket(RestartResponse.valueOf(true));

        // Send character list.
        CharacterService.getInstance().showCharacterList(client);
    }
}
