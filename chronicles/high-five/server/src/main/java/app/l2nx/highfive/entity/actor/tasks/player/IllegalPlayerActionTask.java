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
package app.l2nx.highfive.entity.actor.tasks.player;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.IllegalActionPunishmentType;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentTask;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.serverpackets.LeaveWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task that handles illegal player actions.
 */
public class IllegalPlayerActionTask implements Runnable {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit");

    private final String message;
    private final IllegalActionPunishmentType punishment;
    private final Player actor;

    public IllegalPlayerActionTask(Player actor, String message, IllegalActionPunishmentType punishment) {
        this.message = message;
        this.punishment = punishment;
        this.actor = actor;

        switch (punishment) {
            case KICK: {
                this.actor.sendMessage("You will be kicked for illegal action, GM informed.");
                break;
            }
            case KICKBAN: {
                if (!this.actor.isGM()) {
                    this.actor.setAccessLevel(-1);
                    this.actor.setAccountAccesslevel(-1);
                }

                this.actor.sendMessage("You are banned for illegal action, GM informed.");
                break;
            }
            case JAIL: {
                this.actor.sendMessage("Illegal action performed!");
                this.actor.sendMessage("You will be teleported to GM Consultation Service area and jailed.");
                break;
            }
        }
    }

    @Override
    public void run() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AUDIT, ");
        sb.append(message);
        sb.append(", ");
        sb.append(actor);
        sb.append(", ");
        sb.append(punishment);
        AUDIT_LOG.info(sb.toString());

        if (!actor.isGM()) {
            switch (punishment) {
                case BROADCAST: {
                    AdminData.getInstance().broadcastMessageToGMs(message);
                    return;
                }
                case KICK: {
                    Disconnection.of(actor).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                    break;
                }
                case KICKBAN: {
                    PunishmentManager.getInstance()
                            .startPunishment(new PunishmentTask(
                                    actor.getObjectId(),
                                    PunishmentAffect.CHARACTER,
                                    PunishmentType.BAN,
                                    System.currentTimeMillis() + (GeneralConfig.DEFAULT_PUNISH_PARAM * 1000),
                                    message,
                                    getClass().getSimpleName()));
                    break;
                }
                case JAIL: {
                    PunishmentManager.getInstance()
                            .startPunishment(new PunishmentTask(
                                    actor.getObjectId(),
                                    PunishmentAffect.CHARACTER,
                                    PunishmentType.JAIL,
                                    System.currentTimeMillis() + (GeneralConfig.DEFAULT_PUNISH_PARAM * 1000),
                                    message,
                                    getClass().getSimpleName()));
                    break;
                }
            }
        }
    }
}
