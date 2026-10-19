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

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.managers.ZoneBuildManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerMoveRequest;
import app.l2nx.highfive.mechanics.events.returns.TerminateReturn;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ActionFailed;

public class MoveToLocation extends ClientPacket {
    private int targetX;
    private int targetY;
    private int targetZ;
    private int originX;
    private int originY;
    private int originZ;
    private int movementMode;

    @Override
    protected void readImpl() {
        targetX = readInt();
        targetY = readInt();
        targetZ = readInt();
        originX = readInt();
        originY = readInt();
        originZ = readInt();
        movementMode = readInt(); // is 0 if cursor keys are used 1 if mouse is used
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (player.isOverloaded()) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_MOVE_YOU_ARE_TOO_ENCUMBERED);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if ((PlayerConfig.PLAYER_MOVEMENT_BLOCK_TIME > 0)
                && !player.isGM()
                && (player.getNotMoveUntil() > System.currentTimeMillis())) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_MOVE_WHILE_SPEAKING_TO_AN_NPC_ONE_MOMENT_PLEASE);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if ((targetX == originX) && (targetY == originY) && (targetZ == originZ)) {
            player.stopMove(player.getLocation());
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Check if target location is obstructed.
        if (GeoEngine.getInstance()
                .isCompletelyBlocked(GeoEngine.getGeoX(targetX), GeoEngine.getGeoY(targetY), targetZ)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Check for possible door logout and move over exploit. Also checked at ValidatePosition.
        if (DoorData.getInstance()
                .checkIfDoorsBetween(player.getLastServerPosition(), player.getLocation(), player.getInstanceId())) {
            player.stopMove(player.getLastServerPosition());
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (movementMode == 1) {
            player.setCursorKeyMovement(false);

            if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_MOVE_REQUEST, player)) {
                final TerminateReturn terminate = EventDispatcher.getInstance()
                        .notifyEvent(
                                new OnPlayerMoveRequest(player, new Location(targetX, targetY, targetZ)),
                                player,
                                TerminateReturn.class);
                if ((terminate != null) && terminate.terminate()) {
                    player.sendPacket(ActionFailed.STATIC_PACKET);
                    return;
                }
            }
        } else // 0
        {
            if (!PlayerConfig.ENABLE_KEYBOARD_MOVEMENT && !player.inObserverMode()) {
                return;
            }

            final int dx = originX - player.getX();
            final int dy = originY - player.getY();
            if (((dx * dx) + (dy * dy)) < 1000000) // 1000 distance ^2
            {
                player.setSyncedXYZ(originX, originY, originZ);
            }

            player.setCursorKeyMovement(true);
            player.setLastServerPosition(player.getX(), player.getY(), player.getZ());
        }

        final int teleMode = player.getTeleMode();
        if (teleMode > 0) {
            if (teleMode == 3) // Admin zone build.
            {
                ZoneBuildManager.getInstance().addPoint(player, new Location(targetX, targetY, targetZ));
                player.sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }

            if (teleMode == 1) {
                player.setTeleMode(0);
            }

            player.sendPacket(ActionFailed.STATIC_PACKET);
            player.teleToLocation(new Location(targetX, targetY, targetZ));
            return;
        }

        // Can't move if character is confused.
        if (player.isOutOfControl()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Can't move if character is trying to move a huge distance.
        final double dx = targetX - player.getX();
        final double dy = targetY - player.getY();
        if (((dx * dx) + (dy * dy)) > 98010000) // 9900 distance ^2
        {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Finally move to the target location.
        player.getAI().setIntentionMoveTo(new Location(targetX, targetY, targetZ));

        // Mobius: Check spawn protections.
        player.onActionRequest();
    }
}
