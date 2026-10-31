/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;

public record RestartResponse(boolean accepted) implements GameServerPacket {
    public static RestartResponse read(ByteBuf body) {
        return new RestartResponse(Bodies.parse("RestartResponse", body, ByteBuf::readIntLE) != 0);
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.RESTART_RESPONSE.opcode());
        out.writeIntLE(accepted ? 1 : 0);
    }
}
