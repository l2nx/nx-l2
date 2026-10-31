/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Objects;
import lombok.Builder;

/** Sent in clear. Only the first half of the XOR key travels; both sides append the same fixed tail. */
@Builder(toBuilder = true)
public record KeyPacket(boolean accepted, byte[] key, int serverId) implements GameServerPacket {
    public static final int KEY_BYTES = 8;

    public KeyPacket {
        Objects.requireNonNull(key, "key");
        if (key.length != KEY_BYTES) {
            throw new IllegalArgumentException("The key packet carries " + KEY_BYTES + " key bytes, not " + key.length);
        }
        key = key.clone();
    }

    public static KeyPacket read(ByteBuf body) {
        return Bodies.parse("KeyPacket", body, in -> {
            final boolean accepted = in.readByte() != 0;
            final byte[] key = new byte[KEY_BYTES];
            in.readBytes(key);
            in.skipBytes(4);
            final int serverId = in.readIntLE();
            in.skipBytes(5);
            return builder().accepted(accepted).key(key).serverId(serverId).build();
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.VERSION_CHECK.opcode());
        out.writeByte(accepted ? 1 : 0);
        out.writeBytes(key);
        // The client turns its XOR on when this flag is 1; a refused client gets no key to turn on.
        out.writeIntLE(accepted ? 1 : 0);
        out.writeIntLE(serverId);
        out.writeByte(1);
        out.writeIntLE(0); // obfuscation key
    }

    @Override
    public byte[] key() {
        return key.clone();
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof KeyPacket that)
                && (accepted == that.accepted)
                && Arrays.equals(key, that.key)
                && (serverId == that.serverId);
    }

    @Override
    public int hashCode() {
        return (31 * Objects.hash(accepted, serverId)) + Arrays.hashCode(key);
    }

    /** Never prints the key. */
    @Override
    public String toString() {
        return "KeyPacket[accepted=" + accepted + ", serverId=" + serverId + "]";
    }
}
