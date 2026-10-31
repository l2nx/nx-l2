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

import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ValidateLocation;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.concurrent.TimeUnit;

public class ValidatePosition extends ClientPacket {
    private int x;
    private int y;
    private int z;
    private int heading;

    @Override
    protected void readImpl() {
        x = readInt();
        y = readInt();
        z = readInt();
        heading = readInt();
        readInt(); // vehicle id
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if ((player == null) || player.isTeleporting() || player.inObserverMode() || player.isCastingNow()) {
            return;
        }

        final int realX = player.getX();
        if ((x == 0) && (y == 0) && (realX != 0)) {
            return;
        }

        if (player.isInVehicle()) {
            return;
        }

        if (player.isFalling(z)) {
            return; // Disable validations during fall to avoid "jumping".
        }

        // Abnormal z read from client.
        if ((z < -20000) || (z > 20000)) {
            final Location lastServerPosition = player.getLastServerPosition();
            if (lastServerPosition != null) {
                player.teleToLocation(lastServerPosition);
            }
            return;
        }

        // Don't allow flying transformations outside gracia area!
        if (player.isFlyingMounted() && (x > World.GRACIA_MAX_X)) {
            player.untransform();
        }

        final int realY = player.getY();
        int realZ = player.getZ();
        final int dx = x - realX;
        final int dy = y - realY;
        final int dz = z - realZ;
        final double diffSq = ((dx * dx) + (dy * dy));
        if (player.isFlying() || player.isInsideZone(ZoneId.WATER)) {
            player.setXYZ(realX, realY, z);
            if (diffSq > 90000) {
                player.sendPacket(new ValidateLocation(player));
            }
        } else if (diffSq < 360000) // If too large, messes observation.
        {
            if ((diffSq > 250000) || (Math.abs(dz) > 200)) {
                if ((Math.abs(dz) > 200) && (Math.abs(dz) < 1500) && (Math.abs(z - player.getClientZ()) < 800)) {
                    player.setXYZ(realX, realY, z);
                    realZ = z;
                } else {
                    player.sendPacket(new ValidateLocation(player));
                }
            }
        }

        // Check out of sync.
        if (player.calculateDistance3D(x, y, z) > player.getStat().getMoveSpeed()) {
            if (player.isBlinkActive()) {
                WorldPartition.loop().schedule(() -> player.setBlinkActive(false), 100, TimeUnit.MILLISECONDS);
            } else {
                player.setXYZ(x, y, realZ > z ? GeoEngine.getInstance().getHeight(x, y, realZ) : z);
            }
        }

        player.setClientX(x);
        player.setClientY(y);
        player.setClientZ(z);
        player.setClientHeading(heading); // No real need to validate heading.

        // Mobius: Check for possible door logout and move over exploit. Also checked at MoveToLocation.
        if (!DoorData.getInstance().checkIfDoorsBetween(realX, realY, realZ, x, y, z, player.getInstanceId(), false)) {
            player.setLastServerPosition(realX, realY, realZ);
        }

        if (player.isCursorKeyMovement()) {
            // Use setSyncedXYZ so the active MoveData's xAccurate/yAccurate are updated alongside _location.
            // Plain setXYZ would let the next MovementSystems tick snap the player back to the stale xAccurate /
            // yAccurate that MovementSystems tracks independently of getX() / getY().
            player.setSyncedXYZ(x, y, z);
            player.broadcastPacket(new ValidateLocation(player));
        }
    }
}
