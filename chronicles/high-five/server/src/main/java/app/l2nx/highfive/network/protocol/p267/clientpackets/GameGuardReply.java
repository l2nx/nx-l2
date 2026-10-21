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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/**
 * Format: c dddd
 * @author KenM
 */
@Slf4j
public class GameGuardReply extends ClientPacket {
    private static final byte[] VALID = {
        (byte) 0x88,
        0x40,
        0x1c,
        (byte) 0xa7,
        (byte) 0x83,
        0x42,
        (byte) 0xe9,
        0x15,
        (byte) 0xde,
        (byte) 0xc3,
        0x68,
        (byte) 0xf6,
        0x2d,
        0x23,
        (byte) 0xf1,
        0x3f,
        (byte) 0xee,
        0x68,
        0x5b,
        (byte) 0xc5,
    };

    private final byte[] reply = new byte[8];

    @Override
    protected void readImpl() {
        reply[0] = readByte();
        reply[1] = readByte();
        reply[2] = readByte();
        reply[3] = readByte();
        readInt();
        reply[4] = readByte();
        reply[5] = readByte();
        reply[6] = readByte();
        reply[7] = readByte();
    }

    @Override
    protected void runImpl() {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA");
            final byte[] result = md.digest(reply);
            if (Arrays.equals(result, VALID)) {
                getClient().setGameGuardOk(true);
            }
        } catch (NoSuchAlgorithmException e) {
            log.warn("{}: {}", getClass().getSimpleName(), e.getMessage());
        }
    }
}
