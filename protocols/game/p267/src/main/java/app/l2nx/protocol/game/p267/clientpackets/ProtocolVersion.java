/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record ProtocolVersion(int version) {
    public static ProtocolVersion decode(ByteBuf body) {
        return Bodies.parse("ProtocolVersion", body, in -> new ProtocolVersion(in.readIntLE()));
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.PROTOCOL_VERSION.opcode());
        out.writeIntLE(version);
    }
}
