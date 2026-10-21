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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.entity.actor.instance.AirShip;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class ExAirShipInfo extends ServerPacket {
    // Store some parameters, because they can be changed during broadcast.
    private final AirShip ship;
    private final int x;
    private final int y;
    private final int z;
    private final int heading;
    private final int moveSpeed;
    private final int rotationSpeed;
    private final int captain;
    private final int helm;

    public ExAirShipInfo(AirShip ship) {
        this.ship = ship;
        x = ship.getX();
        y = ship.getY();
        z = ship.getZ();
        heading = ship.getHeading();
        moveSpeed = (int) ship.getStat().getMoveSpeed();
        rotationSpeed = (int) ship.getStat().getRotationSpeed();
        captain = ship.getCaptainId();
        helm = ship.getHelmObjectId();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.EX_AIRSHIP_INFO.writeId(this, buffer);
        buffer.writeInt(ship.getObjectId());
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(heading);
        buffer.writeInt(captain);
        buffer.writeInt(moveSpeed);
        buffer.writeInt(rotationSpeed);
        buffer.writeInt(helm);
        if (helm != 0) {
            // TODO: unhardcode these!
            buffer.writeInt(0x16e); // Controller X
            buffer.writeInt(0x00); // Controller Y
            buffer.writeInt(0x6b); // Controller Z
            buffer.writeInt(0x15c); // Captain X
            buffer.writeInt(0x00); // Captain Y
            buffer.writeInt(0x69); // Captain Z
        } else {
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
        }

        buffer.writeInt(ship.getFuel());
        buffer.writeInt(ship.getMaxFuel());
    }
}
