/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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

import app.l2nx.login.GameServerThread;
import app.l2nx.login.controller.LoginController;
import app.l2nx.network.packet.SimpleReadablePacket;
import java.util.UUID;

/**
 * Thanks to mochitto.
 * @author mrTJO
 */
public class ReplyCharacters extends SimpleReadablePacket {
    public ReplyCharacters(byte[] decrypt, GameServerThread server) {
        super(decrypt);
        readByte(); // Packet id, it is already processed.

        final UUID accountId = new UUID(readLong(), readLong());
        final int chars = readByte();
        final int charsToDel = readByte();
        final long[] charsList = new long[charsToDel];
        for (int i = 0; i < charsToDel; i++) {
            charsList[i] = readLong();
        }

        LoginController.getInstance().setCharactersOnServer(accountId, chars, charsList, server.getServerId());
    }
}
