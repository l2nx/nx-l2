/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
package app.l2nx.highfive.scripts.handlers.punishments;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IPunishmentHandler;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.mechanics.punishment.PunishmentTask;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.LeaveWorld;
import java.util.UUID;

/**
 * This class handles ban punishment.
 * @author UnAfraid
 */
public class BanHandler implements IPunishmentHandler {
    @Override
    public void onStart(PunishmentTask task) {
        switch (task.getAffect()) {
            case CHARACTER: {
                final int objectId = Integer.parseInt(String.valueOf(task.getKey()));
                final Player player = World.getPlayer(objectId);
                if (player != null) {
                    applyToPlayer(player);
                }
                break;
            }
            case ACCOUNT: {
                final GameClient client = LoginService.getInstance().findClient((UUID) task.getKey());
                if (client != null) {
                    final Player player = client.getPlayer();
                    if (player != null) {
                        applyToPlayer(player);
                    } else {
                        Disconnection.of(client).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                    }
                }
                break;
            }
            case IP: {
                final String ip = String.valueOf(task.getKey());
                for (Player player : World.getPlayers()) {
                    if (player.getIPAddress().equals(ip)) {
                        applyToPlayer(player);
                    }
                }
                break;
            }
            case HWID: {
                final String hwid = String.valueOf(task.getKey());
                for (Player player : World.getPlayers()) {
                    final GameClient client = player.getClient();
                    if ((client != null)
                            && client.getHardwareInfo().getMacAddress().equals(hwid)) {
                        applyToPlayer(player);
                    }
                }
                break;
            }
        }
    }

    @Override
    public void onEnd(PunishmentTask task) {
        // Should not do anything.
    }

    /**
     * Applies all punishment effects from the player.
     * @param player
     */
    private void applyToPlayer(Player player) {
        Disconnection.of(player).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
    }

    @Override
    public PunishmentType getType() {
        return PunishmentType.BAN;
    }
}
