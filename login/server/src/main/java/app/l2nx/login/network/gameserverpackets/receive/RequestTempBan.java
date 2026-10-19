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
package app.l2nx.login.network.gameserverpackets.receive;

import app.l2nx.login.controller.LoginController;
import app.l2nx.network.packet.SimpleReadablePacket;

/**
 * @author mrTJO
 */
public class RequestTempBan extends SimpleReadablePacket {
    private final String accountName;
    private final String ip;
    long banTime;

    public RequestTempBan(byte[] decrypt) {
        super(decrypt);
        readByte(); // Packet id, it is already processed.

        accountName = readString();
        ip = readString();
        banTime = readLong();
        final boolean haveReason = readByte() != 0;
        if (haveReason) {
            readString(); // _banReason
        }

        banUser();
    }

    private void banUser() {
        LoginController.getInstance().setAccountTempBan(accountName, banTime);
        LoginController.getInstance().addBanForAddress(ip, banTime);
    }
}
