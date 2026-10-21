/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.clientpackets;

import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;

/** The session id of {@code Init} echoed back, followed by four words of GameGuard data the server does not check. */
public record AuthGameGuard(int sessionId) {
    private static final int GAME_GUARD_DATA = 16;
    private static final int BODY = 4 + GAME_GUARD_DATA;

    public static AuthGameGuard decode(ByteBuf body) {
        if (body.readableBytes() < BODY) {
            throw new PacketDecodeException(
                    "AuthGameGuard needs " + BODY + " bytes, " + body.readableBytes() + " in the frame");
        }
        final AuthGameGuard packet = new AuthGameGuard(body.readIntLE());
        body.skipBytes(GAME_GUARD_DATA);
        return packet;
    }

    public void encode(ByteBuf out) {
        out.writeByte(LoginClientOpcode.AUTH_GAME_GUARD.opcode());
        out.writeIntLE(sessionId);
        out.writeZero(GAME_GUARD_DATA);
    }
}
