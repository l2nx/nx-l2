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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IPunishmentHandler;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.mechanics.punishment.PunishmentTask;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.EtcStatusUpdate;
import java.util.UUID;

/**
 * This class handles chat ban punishment.
 * @author UnAfraid
 */
public class ChatBanHandler implements IPunishmentHandler {
    @Override
    public void onStart(PunishmentTask task) {
        switch (task.getAffect()) {
            case CHARACTER: {
                final int objectId = Integer.parseInt(String.valueOf(task.getKey()));
                final Player player = World.getPlayer(objectId);
                if (player != null) {
                    applyToPlayer(task, player);
                }
                break;
            }
            case ACCOUNT: {
                final GameClient client = LoginService.getInstance().findClient((UUID) task.getKey());
                if (client != null) {
                    final Player player = client.getPlayer();
                    if (player != null) {
                        applyToPlayer(task, player);
                    }
                }
                break;
            }
            case IP: {
                final String ip = String.valueOf(task.getKey());
                for (Player player : World.getPlayers()) {
                    if (player.getIPAddress().equals(ip)) {
                        applyToPlayer(task, player);
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
                        applyToPlayer(task, player);
                    }
                }
                break;
            }
        }
    }

    @Override
    public void onEnd(PunishmentTask task) {
        switch (task.getAffect()) {
            case CHARACTER: {
                final int objectId = Integer.parseInt(String.valueOf(task.getKey()));
                final Player player = World.getPlayer(objectId);
                if (player != null) {
                    removeFromPlayer(player);
                }
                break;
            }
            case ACCOUNT: {
                final GameClient client = LoginService.getInstance().findClient((UUID) task.getKey());
                if (client != null) {
                    final Player player = client.getPlayer();
                    if (player != null) {
                        removeFromPlayer(player);
                    }
                }
                break;
            }
            case IP: {
                final String ip = String.valueOf(task.getKey());
                for (Player player : World.getPlayers()) {
                    if (player.getIPAddress().equals(ip)) {
                        removeFromPlayer(player);
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
                        removeFromPlayer(player);
                    }
                }
                break;
            }
        }
    }

    /**
     * Applies all punishment effects from the player.
     * @param task
     * @param player
     */
    private void applyToPlayer(PunishmentTask task, Player player) {
        final long delay = ((task.getExpirationTime() - ClockBridge.wallMillis()) / 1000) + 1;
        if (delay > 0) {
            final long hours = delay / 3600;
            final long minutes = (delay % 3600) / 60;
            String message = "You've been chat banned for ";
            if (hours > 0) {
                message += hours + " hour" + (hours > 1 ? "'s." : ".");
                if (minutes > 0) {
                    message += " and ";
                }
            }

            if ((minutes > 0) || (hours == 0)) {
                message += minutes + " minute" + (minutes > 1 ? "'s." : ".");
            }

            player.sendPacket(SystemMessageId.CHAT_DISABLED);
            player.sendMessage(message);
        } else {
            player.sendMessage("You've been chat banned forever.");
        }

        player.sendPacket(new EtcStatusUpdate(player));
    }

    /**
     * Removes any punishment effects from the player.
     * @param player
     */
    private void removeFromPlayer(Player player) {
        player.sendPacket(SystemMessageId.CHAT_ENABLED);
        player.sendPacket(new EtcStatusUpdate(player));
    }

    @Override
    public PunishmentType getType() {
        return PunishmentType.CHAT_BAN;
    }
}
