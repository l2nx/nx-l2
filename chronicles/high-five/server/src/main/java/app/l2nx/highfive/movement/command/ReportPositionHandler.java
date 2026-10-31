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
import app.l2nx.engine.core.session.outbound.Audience;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.movement.view.PositionCorrected;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.session.GameContexts;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class ReportPositionHandler implements ContextCommandHandler<Player, ReportPosition> {
    private static final int MAX_CLIENT_Z = 20_000;
    private static final int FLYING_CORRECTION_SQ = 300 * 300;
    private static final int OBSERVATION_LIMIT_SQ = 600 * 600;
    private static final int CORRECTION_SQ = 500 * 500;
    private static final int Z_CORRECTION = 200;
    private static final int Z_FOLLOW_LIMIT = 1_500;
    private static final int CLIENT_Z_DRIFT = 800;
    private static final int BLINK_RESET_MILLIS = 100;

    @Override
    public ContextResolver<Player> context() {
        return GameContexts.WORLD;
    }

    @Override
    public void handle(Player player, ReportPosition command) {
        if (player.isTeleporting() || player.inObserverMode() || player.isCastingNow()) {
            return;
        }

        final int x = command.x();
        final int y = command.y();
        final int z = command.z();
        final int realX = player.getX();
        if ((x == 0) && (y == 0) && (realX != 0)) {
            return;
        }

        if (player.isInVehicle()) {
            return;
        }

        // No validation during a fall, or the player jumps.
        if (player.isFalling(z)) {
            return;
        }

        if ((z < -MAX_CLIENT_Z) || (z > MAX_CLIENT_Z)) {
            final Location lastServerPosition = player.getLastServerPosition();
            if (lastServerPosition != null) {
                player.teleToLocation(lastServerPosition);
            }
            return;
        }

        if (player.isFlyingMounted() && (x > World.GRACIA_MAX_X)) {
            player.untransform();
        }

        final int realY = player.getY();
        int realZ = player.getZ();
        // In double: the client sends any int, and an int square wraps to a small or negative distance.
        final double dx = (double) x - realX;
        final double dy = (double) y - realY;
        final int dz = z - realZ;
        final double diffSq = (dx * dx) + (dy * dy);
        if (player.isFlying() || player.isInsideZone(ZoneId.WATER)) {
            player.setXYZ(realX, realY, z);
            if (diffSq > FLYING_CORRECTION_SQ) {
                player.getPort().emit(PositionCorrected.of(player));
            }
        } else if (diffSq < OBSERVATION_LIMIT_SQ) {
            // A larger difference would disturb observers; it is left to the out-of-sync check below.
            if ((diffSq > CORRECTION_SQ) || (Math.abs(dz) > Z_CORRECTION)) {
                if ((Math.abs(dz) > Z_CORRECTION)
                        && (Math.abs(dz) < Z_FOLLOW_LIMIT)
                        && (Math.abs(z - player.getClientZ()) < CLIENT_Z_DRIFT)) {
                    player.setXYZ(realX, realY, z);
                    realZ = z;
                } else {
                    player.getPort().emit(PositionCorrected.of(player));
                }
            }
        }

        if (player.calculateDistance3D(x, y, z) > player.getStat().getMoveSpeed()) {
            if (player.isBlinkActive()) {
                WorldPartition.loop()
                        .schedule(() -> player.setBlinkActive(false), BLINK_RESET_MILLIS, TimeUnit.MILLISECONDS);
            } else {
                player.setXYZ(x, y, (realZ > z) ? GeoEngine.getInstance().getHeight(x, y, realZ) : z);
            }
        }

        player.setClientX(x);
        player.setClientY(y);
        player.setClientZ(z);
        player.setClientHeading(command.heading());

        if (!DoorData.getInstance().checkIfDoorsBetween(realX, realY, realZ, x, y, z, player.getInstanceId(), false)) {
            player.setLastServerPosition(realX, realY, realZ);
        }

        if (player.isCursorKeyMovement()) {
            // setSyncedXYZ also moves the active move's accurate coordinates; plain setXYZ would let the next
            // MovementSystems tick snap the player back to them.
            player.setSyncedXYZ(x, y, z);
            Audience.emit(PositionCorrected.of(player), player.observerPorts(true));
        }
    }
}
