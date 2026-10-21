/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import app.l2nx.login.network.LoginClient;
import app.l2nx.network.codec.OutgoingPacket;
import app.l2nx.network.codec.PacketWriter;
import app.l2nx.protocol.c621.crypt.LoginFrameCipher;
import app.l2nx.protocol.c621.serverpackets.LoginServerPacket;

public final class OutgoingLoginPacket extends OutgoingPacket<LoginClient> {
    private final LoginServerPacket packet;

    public OutgoingLoginPacket(LoginServerPacket packet) {
        this.packet = packet;
    }

    @Override
    protected int tailroom() {
        return LoginFrameCipher.MAX_OVERHEAD;
    }

    @Override
    protected boolean write(LoginClient client, PacketWriter writer) {
        packet.write(writer.buffer());
        return true;
    }

    @Override
    public String toString() {
        return packet.getClass().getSimpleName();
    }
}
