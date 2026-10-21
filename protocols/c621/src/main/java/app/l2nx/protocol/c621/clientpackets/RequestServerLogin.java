/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.c621.clientpackets;

import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

/** The LoginOk pair of the session key and the chosen game server; bytes past them are ignored. */
@Builder(toBuilder = true)
public record RequestServerLogin(int loginOk1, int loginOk2, int serverId) {
    private static final int BODY = 9;

    public static RequestServerLogin decode(ByteBuf body) {
        if (body.readableBytes() < BODY) {
            throw new PacketDecodeException(
                    "RequestServerLogin needs " + BODY + " bytes, " + body.readableBytes() + " in the frame");
        }
        return RequestServerLogin.builder()
                .loginOk1(body.readIntLE())
                .loginOk2(body.readIntLE())
                .serverId(body.readUnsignedByte())
                .build();
    }

    public void encode(ByteBuf out) {
        out.writeByte(LoginClientOpcode.REQUEST_SERVER_LOGIN.opcode());
        out.writeIntLE(loginOk1);
        out.writeIntLE(loginOk2);
        out.writeByte(serverId);
    }
}
