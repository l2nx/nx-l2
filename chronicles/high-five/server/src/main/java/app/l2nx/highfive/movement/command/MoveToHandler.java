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
package app.l2nx.highfive.movement.command;

import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.highfive.action.view.ActionCancelled;
import app.l2nx.highfive.clock.ClockBridge;
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
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.view.MessageShown;
import app.l2nx.highfive.movement.MoveInput;
import app.l2nx.highfive.session.GameContexts;
import org.springframework.stereotype.Component;

@Component
class MoveToHandler implements ContextCommandHandler<Player, MoveTo> {
    private static final int KEYBOARD_SYNC_RANGE_SQ = 1_000 * 1_000;
    private static final int MAX_MOVE_RANGE_SQ = 9_900 * 9_900;
    private static final int TELE_MODE_ONCE = 1;
    private static final int TELE_MODE_ZONE_BUILD = 3;

    @Override
    public ContextResolver<Player> context() {
        return GameContexts.WORLD;
    }

    @Override
    public void handle(Player player, MoveTo command) {
        if (player.isOverloaded()) {
            player.getPort().emit(MessageShown.of(MessageKey.CANNOT_MOVE_TOO_ENCUMBERED));
            player.getPort().emit(ActionCancelled.INSTANCE);
            return;
        }

        if ((PlayerConfig.PLAYER_MOVEMENT_BLOCK_TIME > 0)
                && !player.isGM()
                && (player.getNotMoveUntil() > ClockBridge.gameMillis())) {
            player.getPort().emit(MessageShown.of(MessageKey.CANNOT_MOVE_WHILE_SPEAKING_TO_NPC));
            player.getPort().emit(ActionCancelled.INSTANCE);
            return;
        }

        final int targetX = command.targetX();
        final int targetY = command.targetY();
        final int targetZ = command.targetZ();
        if ((targetX == command.originX()) && (targetY == command.originY()) && (targetZ == command.originZ())) {
            player.stopMove(player.getLocation());
            player.getPort().emit(ActionCancelled.INSTANCE);
            return;
        }

        if (GeoEngine.getInstance()
                .isCompletelyBlocked(GeoEngine.getGeoX(targetX), GeoEngine.getGeoY(targetY), targetZ)) {
            player.getPort().emit(ActionCancelled.INSTANCE);
            return;
        }

        // A door between the last server position and the current one is the door logout exploit.
        if (DoorData.getInstance()
                .checkIfDoorsBetween(player.getLastServerPosition(), player.getLocation(), player.getInstanceId())) {
            player.stopMove(player.getLastServerPosition());
            player.getPort().emit(ActionCancelled.INSTANCE);
            return;
        }

        if (command.input() == MoveInput.MOUSE) {
            player.setCursorKeyMovement(false);

            if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_MOVE_REQUEST, player)) {
                final TerminateReturn terminate = EventDispatcher.getInstance()
                        .notifyEvent(
                                new OnPlayerMoveRequest(player, new Location(targetX, targetY, targetZ)),
                                player,
                                TerminateReturn.class);
                if ((terminate != null) && terminate.terminate()) {
                    player.getPort().emit(ActionCancelled.INSTANCE);
                    return;
                }
            }
        } else {
            if (!PlayerConfig.ENABLE_KEYBOARD_MOVEMENT && !player.inObserverMode()) {
                return;
            }

            // In double: an int square wraps, so an origin 65536 away would pass as near and teleport the player.
            final double dx = (double) command.originX() - player.getX();
            final double dy = (double) command.originY() - player.getY();
            if (((dx * dx) + (dy * dy)) < KEYBOARD_SYNC_RANGE_SQ) {
                player.setSyncedXYZ(command.originX(), command.originY(), command.originZ());
            }

            player.setCursorKeyMovement(true);
            player.setLastServerPosition(player.getX(), player.getY(), player.getZ());
        }

        final int teleMode = player.getTeleMode();
        if (teleMode > 0) {
            if (teleMode == TELE_MODE_ZONE_BUILD) {
                ZoneBuildManager.getInstance().addPoint(player, new Location(targetX, targetY, targetZ));
                player.getPort().emit(ActionCancelled.INSTANCE);
                return;
            }

            if (teleMode == TELE_MODE_ONCE) {
                player.setTeleMode(0);
            }

            player.getPort().emit(ActionCancelled.INSTANCE);
            player.teleToLocation(new Location(targetX, targetY, targetZ));
            return;
        }

        if (player.isOutOfControl()) {
            player.getPort().emit(ActionCancelled.INSTANCE);
            return;
        }

        final double dx = (double) targetX - player.getX();
        final double dy = (double) targetY - player.getY();
        if (((dx * dx) + (dy * dy)) > MAX_MOVE_RANGE_SQ) {
            player.getPort().emit(ActionCancelled.INSTANCE);
            return;
        }

        player.getAI().setIntentionMoveTo(new Location(targetX, targetY, targetZ));

        player.onActionRequest();
    }
}
