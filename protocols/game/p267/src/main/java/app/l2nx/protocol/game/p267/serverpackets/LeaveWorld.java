/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import io.netty.buffer.ByteBuf;

public record LeaveWorld() implements GameServerPacket {
    public static final LeaveWorld INSTANCE = new LeaveWorld();

    public static LeaveWorld read(ByteBuf body) {
        return INSTANCE;
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.LOG_OUT_OK.opcode());
    }
}
