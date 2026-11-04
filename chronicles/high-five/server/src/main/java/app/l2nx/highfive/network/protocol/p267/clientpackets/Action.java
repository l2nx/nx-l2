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

import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Action extends ClientPacket {
    private int objectId;

    private int actionId;

    @Override
    protected void readImpl() {
        final app.l2nx.protocol.game.p267.clientpackets.Action packet =
                app.l2nx.protocol.game.p267.clientpackets.Action.decode(frame());
        objectId = packet.objectId();
        actionId = packet.actionId();
    }

    @Override
    protected void runImpl() {
        if (!getClient().getFloodProtectors().canPerformPlayerAction()) {
            return;
        }

        // Get the current Player of the player.
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (player.inObserverMode()) {
            player.sendPacket(SystemMessageId.OBSERVERS_CANNOT_PARTICIPATE);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final BuffInfo info = player.getEffectList().getBuffInfoByAbnormalType(AbnormalType.BOT_PENALTY);
        if (info != null) {
            for (AbstractEffect effect : info.getEffects()) {
                if (!effect.checkCondition(-4)) {
                    player.sendPacket(
                            SystemMessageId
                                    .YOU_HAVE_BEEN_REPORTED_AS_AN_ILLEGAL_PROGRAM_USER_SO_YOUR_ACTIONS_HAVE_BEEN_RESTRICTED);
                    player.sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                }
            }
        }

        final WorldObject obj;
        if (player.getTargetId() == objectId) {
            obj = player.getTarget();
        } else if (player.isInAirShip() && (player.getAirShip().getHelmObjectId() == objectId)) {
            obj = player.getAirShip();
        } else {
            obj = World.findObject(objectId);
        }

        // If object requested does not exist, add warn msg into logs.
        if (obj == null) {
            // Pressing e.g. pickup many times quickly would get you here.
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (!obj.isTargetable() && !player.isGM()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Players can't interact with objects in the other instances, except from multiverse.
        if ((obj.getInstanceId() != player.getInstanceId()) && (player.getInstanceId() != -1)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Only GMs can directly interact with invisible characters.
        if (!obj.isVisibleFor(player)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Check if the target is valid, if the player haven't a shop or isn't the requester of a transaction (ex :
        // FriendInvite, JoinAlly, JoinParty...).
        if (player.getActiveRequester() != null) {
            // Actions prohibited when in trade.
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        player.onActionRequest();

        switch (actionId) {
            case 0: {
                obj.onAction(player);
                break;
            }
            case 1: {
                if (!player.isGM() && (!(obj.isNpc() && NpcConfig.ALT_GAME_VIEWNPC) || obj.isFakePlayer())) {
                    obj.onAction(player, false);
                } else {
                    obj.onActionShift(player);
                }
                break;
            }
            default: {
                // Invalid action detected (probably client cheating), log this.
                log.warn("[C] Action: Character: {} requested invalid action: {}", player.getName(), actionId);
                player.sendPacket(ActionFailed.STATIC_PACKET);
                break;
            }
        }
    }
}
