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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.sql.OfflineTraderTable;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.instancezone.Instance;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.mechanics.variables.PlayerVariables;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.LeaveWorld;
import java.util.logging.Logger;

/**
 * @version $Revision: 1.9.4.3 $ $Date: 2005/03/27 15:29:30 $
 */
public class Logout extends ClientPacket {
    protected static final Logger LOGGER_ACCOUNTING = Logger.getLogger("accounting");

    @Override
    protected void readImpl() {}

    @Override
    protected void runImpl() {
        final GameClient client = getClient();
        final Player player = client.getPlayer();
        if (player == null) {
            client.disconnect();
            return;
        }

        if (!player.canLogout()) {
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

        if (OfflineTraderTable.getInstance().enteredOfflineMode(player)) {
            LOGGER_ACCOUNTING.info("Entered offline mode, " + client);
        } else {
            Disconnection.of(client, player).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
        }
    }
}
