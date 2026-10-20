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
package app.l2nx.highfive.scripts.handlers.punishments;

import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.tasks.player.TeleportTask;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.type.JailZone;
import app.l2nx.highfive.handler.IPunishmentHandler;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.mechanics.events.Containers;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerLogin;
import app.l2nx.highfive.mechanics.events.listeners.ConsumerEventListener;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentTask;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.UUID;
import org.apache.commons.lang3.time.DurationFormatUtils;

/**
 * This class handles jail punishment.
 * @author UnAfraid, Skache
 */
public class JailHandler implements IPunishmentHandler {
    public JailHandler() {
        // Register global listener.
        Containers.Global()
                .addListener(new ConsumerEventListener(
                        Containers.Global(),
                        EventType.ON_PLAYER_LOGIN,
                        (OnPlayerLogin event) -> onPlayerLogin(event),
                        this));
    }

    private void onPlayerLogin(OnPlayerLogin event) {
        final Player player = event.player();

        if (player.isJailed() && !player.isInsideZone(ZoneId.JAIL)) {
            applyToPlayer(null, player);
        } else if (player.isJailed()) {
            // Notify about remaining jail time.
            PunishmentTask task = PunishmentManager.getInstance()
                    .getPunishment(player.getObjectId(), PunishmentAffect.CHARACTER, PunishmentType.JAIL);
            if (task != null) {
                long timeRemaining = task.getExpirationTime() - ClockBridge.wallMillis();
                if (timeRemaining > 0) {
                    // Round up to nearest second to avoid showing 1 second less.
                    timeRemaining = ((timeRemaining + 999) / 1000) * 1000;

                    String message = "Jail time remaining: "
                            + DurationFormatUtils.formatDurationWords(timeRemaining, true, true) + ".";
                    player.sendMessage(message);
                } else {
                    player.sendMessage("You are jailed forever.");
                }
            }
        } else if (!player.isJailed() && player.isInsideZone(ZoneId.JAIL) && !player.isGM()) {
            removeFromPlayer(player);
        }
    }

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
        player.setInstanceId(0);

        if (OlympiadManager.getInstance().isRegisteredInComp(player)) {
            OlympiadManager.getInstance().removeDisconnectedCompetitor(player);
        }

        ThreadPool.schedule(new TeleportTask(player, JailZone.getLocationIn()), 2000);

        // Show jail reason HTML message.
        final NpcHtmlMessage msg = new NpcHtmlMessage();
        String content = HtmCache.getInstance().getHtm(player, "data/html/jail_in.htm");
        if (content != null) {
            content = content.replace("%reason%", task != null ? task.getReason() : "");
            content = content.replace("%punishedBy%", task != null ? task.getPunishedBy() : "");
            msg.setHtml(content);
        } else {
            msg.setHtml("<html><body>You have been put in jail by an admin.</body></html>");
        }

        player.sendPacket(msg);

        // Inform the player about jail duration.
        if (task != null) {
            long jailDuration = task.getExpirationTime() - ClockBridge.wallMillis();
            if (jailDuration > 0) {
                // Round up to nearest second to avoid showing 1 second less.
                jailDuration = ((jailDuration + 999) / 1000) * 1000;

                String message = "You've been jailed for "
                        + DurationFormatUtils.formatDurationWords(jailDuration, true, true) + "!";
                player.sendMessage(message);
            } else {
                player.sendMessage("You've been jailed forever.");
            }
        }
    }

    /**
     * Removes any punishment effects from the player.
     * @param player
     */
    private void removeFromPlayer(Player player) {
        ThreadPool.schedule(new TeleportTask(player, JailZone.getLocationOut()), 2000);

        // Open a Html message to inform the player.
        final NpcHtmlMessage msg = new NpcHtmlMessage();
        final String content = HtmCache.getInstance().getHtm(player, "data/html/jail_out.htm");
        if (content != null) {
            msg.setHtml(content);
        } else {
            msg.setHtml("<html><body>You are free for now, respect server rules!</body></html>");
        }

        player.sendPacket(msg);
    }

    @Override
    public PunishmentType getType() {
        return PunishmentType.JAIL;
    }
}
