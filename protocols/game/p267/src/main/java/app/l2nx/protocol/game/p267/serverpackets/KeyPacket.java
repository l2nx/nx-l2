/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPackets;
import app.l2nx.network.codec.PacketWriter;

public final class KeyPacketWriter {
    private static final int KEY_BYTES_SENT = 8;

    private KeyPacketWriter() {}

    /** Only the first half of the key travels; both sides append the same fixed tail. */
    public static void write(PacketWriter out, byte[] key, boolean accepted) {
        ServerPackets.VERSION_CHECK.writeId(out);
        out.writeByte(accepted ? 1 : 0);
        for (int i = 0; i < KEY_BYTES_SENT; i++) {
            out.writeByte(key[i]);
        }
        // Encryption is always on; the client turns its XOR on when this flag is 1.
        out.writeInt(1);
        out.writeInt(ServerConfig.SERVER_ID);
        out.writeByte(1);
        out.writeInt(0); // obfuscation key
    }
}
