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
package app.l2nx.login.protocol.c621.serverpackets;

import app.l2nx.login.network.LoginClient;
import app.l2nx.network.codec.OutgoingPacket;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author KenM
 */
public abstract class LoginServerPacket extends OutgoingPacket<LoginClient> {
    // LoginFrameCipher.encryptedLength: header (8 for the static Init, 4 after it), 1..8 bytes of padding and the
    // 8-byte trailer, so at most 24 bytes past the payload.
    private static final int CIPHER_TAILROOM = 24;

    @Override
    protected int tailroom() {
        return CIPHER_TAILROOM;
    }

    @Override
    protected final boolean write(LoginClient client, PacketWriter writer) {
        writeImpl(client, writer);
        return true;
    }

    protected abstract void writeImpl(LoginClient client, PacketWriter buffer);
}
