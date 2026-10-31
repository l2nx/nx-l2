/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @param messageId the client's system message id
 */
public record SystemMessage(int messageId, List<SystemMessageParam> params) implements GameServerPacket {
    public SystemMessage {
        params = List.copyOf(Objects.requireNonNull(params, "params"));
    }

    public static SystemMessage read(ByteBuf body) {
        return Bodies.parse("SystemMessage", body, in -> {
            final int messageId = in.readIntLE();
            final int count = in.readIntLE();
            final List<SystemMessageParam> params = new ArrayList<>(Math.min(count, 16));
            for (int i = 0; i < count; i++) {
                params.add(SystemMessageParam.read(in));
            }
            return new SystemMessage(messageId, params);
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.SYSTEM_MESSAGE.opcode());
        out.writeIntLE(messageId);
        out.writeIntLE(params.size());
        for (SystemMessageParam param : params) {
            out.writeIntLE(param.type());
            param.writeValue(out);
        }
    }
}
