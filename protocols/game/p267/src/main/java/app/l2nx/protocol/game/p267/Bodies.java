/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267;

import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;
import java.util.function.Function;

public final class Bodies {
    private Bodies() {}

    /** A read past the end of a packet body is the client's or server's malformed packet, never our bug. */
    public static <T> T parse(String packet, ByteBuf body, Function<ByteBuf, T> reader) {
        try {
            return reader.apply(body);
        } catch (IndexOutOfBoundsException e) {
            throw new PacketDecodeException(packet + " read past the end of its frame", e);
        }
    }
}
