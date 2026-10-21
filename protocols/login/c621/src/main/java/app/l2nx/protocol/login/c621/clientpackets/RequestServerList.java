/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.clientpackets;

import app.l2nx.network.codec.PacketDecodeException;
import io.netty.buffer.ByteBuf;
import lombok.Builder;

/** The LoginOk pair of the session key; bytes past it are ignored. */
@Builder(toBuilder = true)
public record RequestServerList(int loginOk1, int loginOk2) {
    private static final int BODY = 8;

    public static RequestServerList decode(ByteBuf body) {
        if (body.readableBytes() < BODY) {
            throw new PacketDecodeException(
                    "RequestServerList needs " + BODY + " bytes, " + body.readableBytes() + " in the frame");
        }
        return RequestServerList.builder()
                .loginOk1(body.readIntLE())
                .loginOk2(body.readIntLE())
                .build();
    }

    public void encode(ByteBuf out) {
        out.writeByte(LoginClientOpcode.REQUEST_SERVER_LIST.opcode());
        out.writeIntLE(loginOk1);
        out.writeIntLE(loginOk2);
    }
}
