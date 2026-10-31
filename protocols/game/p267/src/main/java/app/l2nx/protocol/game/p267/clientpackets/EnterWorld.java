/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Objects;

/**
 * The trace route the client measured to the server: {@link #HOPS} hops of {@link #OCTETS} address octets. The 84
 * bytes in front of it carry nothing the server uses.
 */
public record EnterWorld(byte[] trace) {
    public static final int HOPS = 5;
    public static final int OCTETS = 4;

    private static final int TRACE = HOPS * OCTETS;
    private static final int UNKNOWN = 84;

    public EnterWorld {
        Objects.requireNonNull(trace, "trace");
        if (trace.length != TRACE) {
            throw new IllegalArgumentException("A trace has " + TRACE + " octets, not " + trace.length);
        }
        trace = trace.clone();
    }

    public static EnterWorld decode(ByteBuf body) {
        return Bodies.parse("EnterWorld", body, in -> {
            in.skipBytes(UNKNOWN);
            final byte[] trace = new byte[TRACE];
            in.readBytes(trace);
            return new EnterWorld(trace);
        });
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.ENTER_WORLD.opcode());
        out.writeZero(UNKNOWN);
        out.writeBytes(trace);
    }

    public int octet(int hop, int index) {
        return trace[(hop * OCTETS) + index] & 0xFF;
    }

    @Override
    public byte[] trace() {
        return trace.clone();
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof EnterWorld that) && Arrays.equals(trace, that.trace);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(trace);
    }

    @Override
    public String toString() {
        return "EnterWorld[trace=" + Arrays.toString(trace) + "]";
    }
}
